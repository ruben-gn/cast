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
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.PodcastRepository
import cast.android.domain.repository.QueueRepository
import cast.android.network.PlaybackWebSocketClient
import cast.android.ui.MainActivity
import cast.android.widget.NowPlayingWidget
import cast.api.EpisodeDetailDto
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var playbackWebSocketClient: PlaybackWebSocketClient
    @Inject lateinit var queueRepository: QueueRepository
    @Inject lateinit var podcastRepository: PodcastRepository
    @Inject lateinit var episodeRepository: EpisodeRepository

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Library/browse callbacks run off the main thread. When Android Auto connects over the
     * legacy MediaBrowserCompat protocol, Media3's [MediaLibraryServiceLegacyStub] blocks the
     * main thread on `future.get()`; if the future's coroutine were also dispatched to the main
     * thread it could never run, deadlocking the service start (200s ANR → endless spinner).
     */
    private val libraryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> player?.let {
                if (it.isPlaying) it.pause()
                else { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() }
            }
            ACTION_SEEK_BACK -> player?.seekBack()
            ACTION_SEEK_FORWARD -> player?.seekForward()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        stopProgressSync()
        serviceScope.launch { NowPlayingWidget.update(this@PlaybackService, "", "", false, false) }
        serviceScope.cancel()
        libraryScope.cancel()
        playbackWebSocketClient.disconnect()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * Browse tree for Android Auto: root → Recent / Queue / Podcasts → episodes.
     * Episode leaf ids stay the bare episode id everywhere so Auto-initiated playback
     * participates in the same WebSocket progress-sync and "now playing" highlight.
     */
    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> = libraryScope.future {
            LibraryResult.ofItem(browsableItem(ROOT_ID, "Cast"), params)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = libraryScope.future {
            val children: List<MediaItem> = runCatching {
                when {
                    parentId == ROOT_ID -> listOf(
                        browsableItem(RECENT_ID, "Recent"),
                        browsableItem(QUEUE_ID, "Queue"),
                        browsableItem(PODCASTS_ID, "Podcasts"),
                    )
                    parentId == RECENT_ID -> episodeRepository.getRecentEpisodes().map(::playableItem)
                    parentId == QUEUE_ID -> queueRepository.getQueue().map(::playableItem)
                    parentId == PODCASTS_ID ->
                        podcastRepository.listPodcasts().map { podcastItem(it.id, it.name, it.image) }
                    parentId.startsWith(PODCAST_PREFIX) ->
                        podcastRepository.getPodcast(parentId.removePrefix(PODCAST_PREFIX))
                            .episodes.map(::playableItem)
                    else -> emptyList()
                }
            }.getOrElse { e -> Log.w(TAG, "onGetChildren failed for parentId=$parentId", e); emptyList() }
            LibraryResult.ofItemList(children, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> = libraryScope.future {
            val item: MediaItem? = runCatching {
                when {
                    mediaId == ROOT_ID -> browsableItem(ROOT_ID, "Cast")
                    mediaId == RECENT_ID -> browsableItem(RECENT_ID, "Recent")
                    mediaId == QUEUE_ID -> browsableItem(QUEUE_ID, "Queue")
                    mediaId == PODCASTS_ID -> browsableItem(PODCASTS_ID, "Podcasts")
                    mediaId.startsWith(PODCAST_PREFIX) -> {
                        val p = podcastRepository.getPodcast(mediaId.removePrefix(PODCAST_PREFIX))
                        podcastItem(p.id, p.name, p.image)
                    }
                    else -> playableItem(episodeRepository.getEpisode(mediaId))
                }
            }.getOrNull()
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(LibraryResult.RESULT_ERROR_UNKNOWN)
        }

        /**
         * Auto sends media items carrying only a mediaId (the audio URI is stripped over the
         * binder), so resolve those back to a full playable item. In-app items already carry
         * their URI (localConfiguration), so pass them straight through with no extra fetch.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = libraryScope.future {
            mediaItems.map { item ->
                if (item.localConfiguration != null) item
                else runCatching { playableItem(episodeRepository.getEpisode(item.mediaId)) }
                    .getOrDefault(item)
            }.toMutableList()
        }
    }

    private fun browsableItem(id: String, title: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
        return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
    }

    private fun podcastItem(id: String, name: String, image: String?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(name)
            .setArtworkUri(image?.toUri())
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PODCASTS)
            .build()
        return MediaItem.Builder().setMediaId(PODCAST_PREFIX + id).setMediaMetadata(metadata).build()
    }

    private fun playableItem(episode: EpisodeDetailDto): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastName)
            .setArtworkUri(episode.podcastImage?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(episode.audioUrl)
            .setMediaMetadata(metadata)
            .build()
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
            mediaSession?.player?.let { player ->
                player.setMediaItem(playableItem(next))
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

        private const val ROOT_ID = "root"
        private const val RECENT_ID = "recent"
        private const val QUEUE_ID = "queue"
        private const val PODCASTS_ID = "podcasts"
        private const val PODCAST_PREFIX = "podcast/"
    }
}
