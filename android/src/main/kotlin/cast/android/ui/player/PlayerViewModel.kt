package cast.android.ui.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cast.android.media.CastMediaLibraryService
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ViewModel() {

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem
            _currentPosition.value = 0L
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            _currentPosition.value = newPosition.positionMs
        }
    }

    init {
        viewModelScope.launch {
            val token = SessionToken(context, ComponentName(context, CastMediaLibraryService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            val deferred = CompletableDeferred<MediaController>()
            future.addListener({
                try { deferred.complete(future.get()) }
                catch (e: Exception) { deferred.completeExceptionally(e) }
            }, ContextCompat.getMainExecutor(context))
            val ctrl = deferred.await()
            ctrl.addListener(playerListener)
            _controller.value = ctrl
        }
    }

    fun playEpisode(episode: EpisodeDetailDto, artworkUrl: String) {
        val ctrl = _controller.value ?: return
        val extras = Bundle().apply {
            putLong("durationMs", episode.duration.parseDurationMs() ?: 0L)
        }
        val item = MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(episode.audioUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(episode.title)
                    .setDescription(episode.description)
                    .setArtworkUri(Uri.parse(artworkUrl))
                    .setExtras(extras)
                    .build()
            )
            .build()
        ctrl.setMediaItem(item)
        ctrl.prepare()
        ctrl.play()
    }

    override fun onCleared() {
        _controller.value?.removeListener(playerListener)
        _controller.value?.release()
        super.onCleared()
    }
}

private fun String?.parseDurationMs(): Long? {
    if (this == null) return null
    val parts = split(":").mapNotNull { it.toLongOrNull() }
    return when (parts.size) {
        2 -> (parts[0] * 60 + parts[1]) * 1000
        3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
        else -> null
    }
}
