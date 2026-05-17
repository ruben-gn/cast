package cast.android.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cast.android.network.PlaybackWebSocketClient
import cast.android.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject lateinit var playbackWebSocketClient: PlaybackWebSocketClient

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var progressJob: Job? = null
    private var currentEpisodeId: String? = null
    private var episodeStarted = false

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also { it.addListener(PlayerListener()) }

        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        playbackWebSocketClient.connect()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        stopProgressSync()
        serviceScope.cancel()
        playbackWebSocketClient.disconnect()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private inner class PlayerListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentEpisodeId = mediaItem?.mediaId
            episodeStarted = false
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val episodeId = currentEpisodeId ?: return
            if (isPlaying) {
                if (!episodeStarted) {
                    val startMs = mediaSession?.player?.currentPosition ?: 0
                    sendWs("""{"type":"start","episodeId":"$episodeId","startPositionMs":$startMs}""")
                    episodeStarted = true
                }
                startProgressSync(episodeId)
            } else {
                stopProgressSync()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                currentEpisodeId?.let { sendWs("""{"type":"ended","episodeId":"$it"}""") }
                stopProgressSync()
            }
        }
    }

    private fun startProgressSync(episodeId: String) {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive) {
                delay(10_000)
                val progressMs = mediaSession?.player?.currentPosition ?: break
                sendWs("""{"type":"update","episodeId":"$episodeId","progressMs":$progressMs}""")
            }
        }
    }

    private fun stopProgressSync() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun sendWs(message: String) = playbackWebSocketClient.send(message)
}
