package cast.android.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import cast.android.domain.repository.DownloadRepository
import cast.android.domain.repository.EpisodeRepository
import cast.android.service.PlaybackService
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val episodeRepository: EpisodeRepository,
    private val downloadRepository: DownloadRepository,
    private val dataStore: DataStore<Preferences>,
) : ViewModel() {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private val _currentArtworkUrl = MutableStateFlow<String?>(null)
    val currentArtworkUrl: StateFlow<String?> = _currentArtworkUrl.asStateFlow()

    private val _currentDescription = MutableStateFlow<String?>(null)
    val currentDescription: StateFlow<String?> = _currentDescription.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _lastKnownProgress = MutableStateFlow<Map<String, Pair<Long, Long>>>(emptyMap())
    val lastKnownProgress: StateFlow<Map<String, Pair<Long, Long>>> = _lastKnownProgress.asStateFlow()

    private val _episodeCompleted = MutableSharedFlow<String>(replay = 0)
    val episodeCompleted: SharedFlow<String> = _episodeCompleted.asSharedFlow()

    // Emitted when the app connects with an empty player and there is no remembered episode to
    // restore. The Now Playing screen listens so a widget cold-start lands on Recent instead of a
    // blank "Nothing playing" screen.
    private val _noEpisodeToRestore = MutableSharedFlow<Unit>(replay = 0)
    val noEpisodeToRestore: SharedFlow<Unit> = _noEpisodeToRestore.asSharedFlow()

    @Volatile private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val prevId = _currentMediaItem.value?.mediaId
            val prevPos = _position.value
            val prevDur = _duration.value
            if (prevId != null && prevPos > 0 && prevDur > 0) {
                _lastKnownProgress.value = _lastKnownProgress.value + (prevId to (prevPos to prevDur))
            }
            if (mediaItem?.mediaId != _currentMediaItem.value?.mediaId) {
                _position.value = 0L
            }
            _currentMediaItem.value = mediaItem
            _currentArtworkUrl.value = mediaItem?.mediaMetadata?.artworkUri?.toString()
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                _isPlaying.value = false
            }
            if (state == Player.STATE_ENDED) {
                _currentMediaItem.value?.mediaId?.let { id ->
                    viewModelScope.launch { _episodeCompleted.emit(id) }
                    downloadRepository.remove(id)
                }
            }
            if (state == Player.STATE_READY) {
                val ctrl = controller ?: return
                _position.value = ctrl.currentPosition
                _duration.value = ctrl.duration.coerceAtLeast(0L)
            }
        }
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            _position.value = newPosition.positionMs
        }
    }

    init {
        connectToService()
        viewModelScope.launch {
            while (true) {
                delay(500)
                val ctrl = controller ?: continue
                if (ctrl.isPlaying) {
                    _position.value = ctrl.currentPosition
                    _duration.value = ctrl.duration.coerceAtLeast(0L)
                }
            }
        }
    }

    private fun connectToService() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        viewModelScope.launch(Dispatchers.Main) {
            try {
                val ctrl = withContext(Dispatchers.IO) { future.get() }
                ctrl.addListener(playerListener)
                controller = ctrl
                _isPlaying.value = ctrl.isPlaying
                _currentMediaItem.value = ctrl.currentMediaItem
                _currentArtworkUrl.value = ctrl.currentMediaItem?.mediaMetadata?.artworkUri?.toString()
                _position.value = ctrl.currentPosition
                _duration.value = ctrl.duration.coerceAtLeast(0L)
                // App opened with an empty player (e.g. cold-started from a widget tap): restore the
                // last episode so Now Playing shows what was playing instead of a blank screen.
                if (ctrl.currentMediaItem == null) restoreLastEpisode(ctrl)
            } catch (_: Exception) {}
        }
    }

    /**
     * Load the remembered episode **paused** so Now Playing reflects it; we deliberately don't call
     * play(). Setting the item fires the service's onMediaItemTransition, which syncs position from
     * the server. If nothing is worth restoring, signal so the UI can fall back to Recent.
     */
    private fun restoreLastEpisode(ctrl: MediaController) {
        viewModelScope.launch {
            val id = dataStore.data.first()[PlaybackService.LAST_EPISODE_ID]
            val episode = id?.let { runCatching { episodeRepository.getEpisode(it) }.getOrNull() }
            if (episode == null || episode.played) {
                _noEpisodeToRestore.emit(Unit)
                return@launch
            }
            // Player may have started playing something between connect and now; don't clobber it.
            if (ctrl.currentMediaItem != null) return@launch
            _currentDescription.value = episode.description.ifBlank { null }
            ctrl.setMediaItem(buildMediaItem(episode))
            ctrl.prepare()
        }
    }

    fun playEpisode(episode: EpisodeDetailDto) {
        val ctrl = controller
        if (ctrl != null) {
            if (ctrl.currentMediaItem?.mediaId == episode.id) {
                if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
                return
            }
            _currentArtworkUrl.value = episode.podcastImage
            _currentDescription.value = episode.description.ifBlank { null }
            val mediaItem = buildMediaItem(episode)
            ctrl.setMediaItem(mediaItem)
            ctrl.prepare()
            ctrl.play()
        } else {
            val mediaItem = buildMediaItem(episode)
            controllerFuture?.addListener({
                controller?.let {
                    it.setMediaItem(mediaItem)
                    it.prepare()
                    it.play()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    // customCacheKey must match PlaybackService.playableItem and the DownloadRequest: the service
    // passes in-app items through untouched (localConfiguration != null), so without it a playback
    // started here would look up the caches by URL and miss downloaded episodes.
    @OptIn(UnstableApi::class)
    private fun buildMediaItem(episode: EpisodeDetailDto) = MediaItem.Builder()
        .setMediaId(episode.id)
        .setUri(episode.audioUrl)
        .setCustomCacheKey(episode.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(episode.title)
                .setArtist(episode.podcastName)
                .setArtworkUri(episode.podcastImage?.toUri())
                .build()
        )
        .build()

    fun playPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            if (ctrl.playbackState == Player.STATE_IDLE) ctrl.prepare()
            ctrl.play()
        }
    }

    fun seekForward() { controller?.seekForward() }
    fun seekBack() { controller?.seekBack() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}

val LocalPlayerViewModel = staticCompositionLocalOf<PlayerViewModel> {
    error("No PlayerViewModel provided")
}
