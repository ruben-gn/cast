package cast.android.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
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
import cast.android.widget.NowPlayingWidget
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
                Log.d(TAG, "states.collect: episodeId=${state.episodeId} currentEpisodeId=$currentEpisodeId episodeStarted=$episodeStarted progressMs=${state.progressMs} played=${state.played}")
                if (state.episodeId != currentEpisodeId || episodeStarted) return@collect
                val seekMs = if (state.played) 0L else state.progressMs
                mediaSession?.player?.seekTo(seekMs)
                sendWs("""{"type":"start","episodeId":"${state.episodeId}","startPositionMs":$seekMs}""")
                episodeStarted = true
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> player?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_SEEK_BACK -> player?.seekBack()
            ACTION_SEEK_FORWARD -> player?.seekForward()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        stopProgressSync()
        serviceScope.launch { NowPlayingWidget.update(this@PlaybackService, "", "", false, false) }
        serviceScope.cancel()
        playbackWebSocketClient.disconnect()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun pushWidgetState(isPlaying: Boolean) {
        val item = mediaSession?.player?.currentMediaItem
        serviceScope.launch {
            NowPlayingWidget.update(
                context = this@PlaybackService,
                title = item?.mediaMetadata?.title?.toString() ?: "",
                podcast = item?.mediaMetadata?.artist?.toString() ?: "",
                isPlaying = isPlaying,
                hasEpisode = item != null,
            )
        }
    }

    private inner class PlayerListener : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            Log.d(TAG, "onMediaItemTransition: mediaId=${mediaItem?.mediaId} reason=$reason")
            currentEpisodeId = mediaItem?.mediaId
            episodeStarted = false
            pushWidgetState(mediaSession?.player?.isPlaying ?: false)
            val episodeId = currentEpisodeId ?: return
            sendWs("""{"type":"get","episodeId":"$episodeId"}""")
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.d(TAG, "onPlayWhenReadyChanged: playWhenReady=$playWhenReady reason=$reason episodeStarted=$episodeStarted")
            val episodeId = currentEpisodeId ?: return
            if (playWhenReady) {
                if (episodeStarted) {
                    // Resume after intentional pause: re-sync from server so webapp progress is picked up
                    episodeStarted = false
                    sendWs("""{"type":"get","episodeId":"$episodeId"}""")
                }
                startProgressSync(episodeId)
            } else {
                // Flush exact position on intentional pause so server is never stale
                val progressMs = mediaSession?.player?.currentPosition ?: 0L
                sendWs("""{"type":"update","episodeId":"$episodeId","progressMs":$progressMs}""")
                stopProgressSync()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
            pushWidgetState(isPlaying)
        }

        override fun onPlaybackStateChanged(state: Int) {
            Log.d(TAG, "onPlaybackStateChanged: state=$state")
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

    private fun sendWs(message: String) {
        Log.d(TAG, "sendWs: $message")
        playbackWebSocketClient.send(message)
    }

    companion object {
        private const val TAG = "Cast/Playback"
        const val ACTION_PLAY_PAUSE = "cast.android.widget.PLAY_PAUSE"
        const val ACTION_SEEK_BACK = "cast.android.widget.SEEK_BACK"
        const val ACTION_SEEK_FORWARD = "cast.android.widget.SEEK_FORWARD"
    }
}
