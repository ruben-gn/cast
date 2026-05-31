package cast.android.ui.viewmodel

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cast.android.service.PlaybackService
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    @Volatile private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem
            _position.value = 0L
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) {
                _isPlaying.value = false
            }
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
                _position.value = ctrl.currentPosition
                _duration.value = ctrl.duration.coerceAtLeast(0L)
            } catch (_: Exception) {}
        }
    }

    fun playEpisode(episode: EpisodeDetailDto) {
        val ctrl = controller
        if (ctrl != null) {
            if (ctrl.currentMediaItem?.mediaId == episode.id) {
                if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
                return
            }
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

    private fun buildMediaItem(episode: EpisodeDetailDto) = MediaItem.Builder()
        .setMediaId(episode.id)
        .setUri(episode.audioUrl)
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
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
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
