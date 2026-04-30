package cast.android.media

import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import cast.android.data.PlaybackSyncClient
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class CastMediaLibraryService : MediaLibraryService() {

    @Inject lateinit var syncClient: PlaybackSyncClient

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaLibrarySession
    private var syncJob: Job? = null

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val episodeId = mediaItem?.mediaId ?: return
            scope.launch(Dispatchers.IO) {
                val posMs = syncClient.getPosition(episodeId) ?: return@launch
                withContext(Dispatchers.Main) { player.seekTo(posMs) }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startSync() else stopSync()
        }
    }

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this).build()
        player.addListener(playerListener)
        mediaSession = MediaLibrarySession.Builder(this, player, object : MediaLibrarySession.Callback {}).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) stopSelf()
    }

    override fun onDestroy() {
        stopSync()
        mediaSession.release()
        player.removeListener(playerListener)
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    private fun startSync() {
        syncJob?.cancel()
        syncJob = scope.launch(Dispatchers.IO) {
            while (true) {
                delay(500)
                val (episodeId, posMs) = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId to player.currentPosition
                }
                if (episodeId != null) syncClient.updatePosition(episodeId, posMs)
            }
        }
    }

    private fun stopSync() {
        syncJob?.cancel()
        syncJob = null
    }
}
