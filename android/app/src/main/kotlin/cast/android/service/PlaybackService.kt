package cast.android.service

import android.app.PendingIntent
import android.content.Intent
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import cast.android.domain.repository.QueueRepository
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
    @Inject lateinit var queueRepository: QueueRepository

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

        serviceScope.launch {
            playbackWebSocketClient.states.collect { state ->
                if (state.episodeId != currentEpisodeId || episodeStarted) return@collect
                val seekMs = if (state.played) 0L else state.progressMs
                mediaSession?.player?.seekTo(seekMs)
                sendWs("""{"type":"start","episodeId":"${state.episodeId}","startPositionMs":$seekMs}""")
                episodeStarted = true
            }
        }
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
            val episodeId = currentEpisodeId ?: return
            sendWs("""{"type":"get","episodeId":"$episodeId"}""")
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val episodeId = currentEpisodeId ?: return
            if (isPlaying) startProgressSync(episodeId) else stopProgressSync()
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                currentEpisodeId?.let { sendWs("""{"type":"ended","episodeId":"$it"}""") }
                stopProgressSync()
                playNextInQueue()
            }
        }
    }

    private fun playNextInQueue() {
        serviceScope.launch {
            val queue = try { queueRepository.getQueue() } catch (_: Exception) { return@launch }
            val next = queue.firstOrNull() ?: return@launch
            try { queueRepository.removeFromQueue(next.id) } catch (_: Exception) {}
            val mediaItem = MediaItem.Builder()
                .setMediaId(next.id)
                .setUri(next.audioUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(next.title)
                        .setArtist(next.podcastName)
                        .setArtworkUri(next.podcastImage?.toUri())
                        .build()
                )
                .build()
            mediaSession?.player?.let { player ->
                player.setMediaItem(mediaItem)
                player.prepare()
                player.play()
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
