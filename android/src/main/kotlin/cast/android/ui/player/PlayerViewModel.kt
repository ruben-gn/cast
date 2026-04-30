package cast.android.ui.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import cast.android.media.CastMediaLibraryService
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

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
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

    fun playEpisode(episodeId: String, audioUrl: String) {
        val ctrl = _controller.value ?: return
        ctrl.setMediaItem(MediaItem.Builder().setMediaId(episodeId).setUri(audioUrl).build())
        ctrl.prepare()
        ctrl.play()
    }

    override fun onCleared() {
        _controller.value?.removeListener(playerListener)
        _controller.value?.release()
        super.onCleared()
    }
}
