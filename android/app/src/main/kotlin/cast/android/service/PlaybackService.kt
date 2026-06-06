package cast.android.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaConstants
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class PlaybackService : MediaLibraryService() {

    @Inject lateinit var playbackWebSocketClient: PlaybackWebSocketClient
    @Inject lateinit var queueRepository: QueueRepository
    @Inject lateinit var podcastRepository: PodcastRepository
    @Inject lateinit var episodeRepository: EpisodeRepository
    @Inject lateinit var dataStore: DataStore<Preferences>
    @Inject lateinit var cacheDataSourceFactory: CacheDataSource.Factory

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
    private var resuming = false

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
            .also { it.addListener(PlayerListener()) }

        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        // Android Auto shows skip-to-prev/next by default, which do nothing during single-episode
        // playback. Put rewind/fast-forward in those slots instead. Using player commands (not custom
        // session commands) wires the seek automatically; icons match ExoPlayer's 5s/15s defaults.
        val rewindButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_5)
            .setDisplayName("Rewind")
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val fastForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD_15)
            .setDisplayName("Fast forward")
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(ImmutableList.of(rewindButton, fastForwardButton))
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
                when {
                    it.isPlaying -> it.pause()
                    // Cold start after the app was fully closed: the player is empty and the custom
                    // widget intent never reaches Media3's onPlaybackResumption, so a bare play() just
                    // no-ops. Explicitly load the last-played episode; the WS `get` (onMediaItemTransition)
                    // then re-seeks to the server's position.
                    it.mediaItemCount == 0 -> resumeLastEpisode()
                    else -> { if (it.playbackState == Player.STATE_IDLE) it.prepare(); it.play() }
                }
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
            // Auto requests a "recent" root on connect to surface continue-listening content. Advertise
            // it only when we have a last-played episode. This must stay a cheap LOCAL read: Auto's
            // legacy stub blocks the main thread on this future (see libraryScope above), so a Pi fetch
            // here would risk an ANR. The actual episode is fetched lazily in onGetChildren.
            if (params?.isRecent == true) {
                if (dataStore.data.first()[LAST_EPISODE_ID] != null)
                    LibraryResult.ofItem(browsableItem(RECENT_ROOT_ID, "Cast"), params)
                else
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_NOT_SUPPORTED)
            } else {
                LibraryResult.ofItem(browsableItem(ROOT_ID, "Cast"), params)
            }
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
                    parentId == RECENT_ROOT_ID ->
                        listOfNotNull(lastUnfinishedEpisode()?.let { playableItem(it, completionExtras(it)) })
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
                    mediaId == RECENT_ROOT_ID -> browsableItem(RECENT_ROOT_ID, "Cast")
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

        /**
         * Resume the last-played episode when a controller (Android Auto, Bluetooth, system UI)
         * presses play with no current item — e.g. after the service was killed. Failing the
         * future signals "nothing to resume". The exact start position is non-critical: once the
         * item loads, the WebSocket sync ([onMediaItemTransition] → "get") re-seeks to the server's
         * authoritative progress.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = libraryScope.future {
            Log.d(TAG, "onPlaybackResumption: called isForPlayback=$isForPlayback")
            val episode = lastUnfinishedEpisode()
                ?: throw UnsupportedOperationException("No previous episode to resume")
            Log.d(TAG, "onPlaybackResumption: resuming id=${episode.id} progressMs=${episode.progressMs}")
            MediaSession.MediaItemsWithStartPosition(listOf(playableItem(episode)), 0, episode.progressMs)
        }

        // KEYCODE_MEDIA_PREVIOUS from headphones defaults to seekToPrevious() which jumps to
        // position 0. Reroute it to seekBack() to match the in-app rewind button.
        override fun onMediaButtonEvent(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            intent: Intent,
        ): Boolean {
            val keyEvent: KeyEvent? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            if (keyEvent?.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS &&
                keyEvent.action == KeyEvent.ACTION_DOWN) {
                session.player.seekBack()
                return true
            }
            return super.onMediaButtonEvent(session, controllerInfo, intent)
        }
    }

    /**
     * The episode last played through this device, if still unfinished — the source for both Android
     * Auto's continue-listening (recent) root and playback resumption. One Pi fetch; never call this
     * from [LibraryCallback.onGetLibraryRoot], whose future blocks Auto's main thread.
     */
    private suspend fun lastUnfinishedEpisode(): EpisodeDetailDto? {
        val id = dataStore.data.first()[LAST_EPISODE_ID] ?: run {
            Log.d(TAG, "lastUnfinishedEpisode: LAST_EPISODE_ID is null")
            return null
        }
        val episode = runCatching { episodeRepository.getEpisode(id) }
            .onFailure { Log.w(TAG, "lastUnfinishedEpisode: getEpisode($id) failed", it) }
            .getOrNull()
        Log.d(TAG, "lastUnfinishedEpisode: id=$id fetched=${episode != null} played=${episode?.played}")
        return episode?.takeIf { !it.played }
    }

    /**
     * Resume the last-played episode into an empty player (widget Play after a full app close).
     * Guarded so repeated taps during the Pi fetch don't stack multiple loads; once [setMediaItem]
     * runs the player is non-empty and further taps fall through to plain play/pause.
     */
    private fun resumeLastEpisode() {
        if (resuming) return
        resuming = true
        serviceScope.launch {
            try {
                // We were started via startForegroundService() (widget exemption), so Media3 must reach
                // startForeground() within ~5s — which only happens once play() runs below. Bound the Pi
                // fetch so an asleep/unreachable server fails fast instead of hanging. NOTE: if the fetch
                // still outlasts the deadline, the OS throws ForegroundServiceDidNotStartInTimeException.
                // If that bites in practice, cache the last episode's metadata so we can setMediaItem()
                // locally and go foreground without depending on the Pi being awake.
                val episode = withTimeoutOrNull(4_000) { lastUnfinishedEpisode() } ?: return@launch
                mediaSession?.player?.let { player ->
                    player.setMediaItem(playableItem(episode))
                    player.prepare()
                    player.play()
                }
            } finally {
                resuming = false
            }
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

    private fun playableItem(episode: EpisodeDetailDto, extras: Bundle? = null): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(episode.title)
            .setArtist(episode.podcastName)
            .setArtworkUri(episode.podcastImage?.toUri())
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
            .apply { extras?.let { setExtras(it) } }
            .build()
        return MediaItem.Builder()
            .setMediaId(episode.id)
            .setUri(episode.audioUrl)
            .setCustomCacheKey(episode.id)
            .setMediaMetadata(metadata)
            .build()
    }

    /**
     * Completion-status extras so Android Auto renders a progress bar on the continue-listening
     * item. Auto reads these off the media description; partial progress also needs a percentage.
     */
    private fun completionExtras(episode: EpisodeDetailDto): Bundle = Bundle().apply {
        val durationMs = episode.durationMs
        if (episode.progressMs > 0 && durationMs != null && durationMs > 0) {
            putInt(
                MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_PARTIALLY_PLAYED,
            )
            putDouble(
                MediaConstants.EXTRAS_KEY_COMPLETION_PERCENTAGE,
                (episode.progressMs.toDouble() / durationMs).coerceIn(0.0, 1.0),
            )
        } else {
            putInt(
                MediaConstants.EXTRAS_KEY_COMPLETION_STATUS,
                MediaConstants.EXTRAS_VALUE_COMPLETION_STATUS_NOT_PLAYED,
            )
        }
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
            // Head-start seek from local cache so playback doesn't jump while we fetch the server position.
            // Does NOT set episodeStarted: the WS `get` reconcile (states.collect) still re-seeks to the
            // authoritative server value when it arrives. Server stays the source of truth.
            serviceScope.launch {
                val cached = runCatching { dataStore.data.first()[progressKey(episodeId)] }.getOrNull()
                if (currentEpisodeId != episodeId || episodeStarted) return@launch
                localResumePositionMs(cached, played = false)?.let { mediaSession?.player?.seekTo(it) }
            }
            // Remember it so Auto/Bluetooth can resume after the service is killed (onPlaybackResumption).
            libraryScope.launch { runCatching { dataStore.edit { it[LAST_EPISODE_ID] = episodeId } } }
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
                cacheProgress(episodeId, progressMs)
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
                currentEpisodeId?.let {
                    sendWs("""{"type":"ended","episodeId":"$it"}""")
                    clearCachedProgress(it)
                }
                stopProgressSync()
                playNextInQueue()
            }
        }
    }

    private fun playNextInQueue() {
        serviceScope.launch {
            // The finished episode must leave the player regardless of connectivity, so a failed queue
            // fetch (offline / Pi down) collapses to the same "nothing next" path as an empty queue.
            val next = runCatching { queueRepository.getQueue() }.getOrNull()?.firstOrNull()
            if (next == null) {
                // Clear the finished episode from the player (which also clears the player bar and widget
                // via onMediaItemTransition) and don't let resumption replay it.
                mediaSession?.player?.clearMediaItems()
                runCatching { dataStore.edit { it.remove(LAST_EPISODE_ID) } }
                return@launch
            }
            runCatching { queueRepository.removeFromQueue(next.id) }
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
                delay(1_000)
                val progressMs = mediaSession?.player?.currentPosition ?: break
                cacheProgress(episodeId, progressMs)
                sendWs("""{"type":"update","episodeId":"$episodeId","progressMs":$progressMs}""")
            }
        }
    }

    private fun stopProgressSync() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun progressKey(episodeId: String) = longPreferencesKey("progress_$episodeId")

    private fun cacheProgress(episodeId: String, progressMs: Long) {
        if (progressMs <= 0L) return
        libraryScope.launch {
            runCatching { dataStore.edit { it[progressKey(episodeId)] = progressMs } }
        }
    }

    private fun clearCachedProgress(episodeId: String) {
        libraryScope.launch { runCatching { dataStore.edit { it.remove(progressKey(episodeId)) } } }
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

        private val LAST_EPISODE_ID = stringPreferencesKey("last_episode_id")

        private const val ROOT_ID = "root"
        private const val RECENT_ROOT_ID = "recent_root"
        private const val RECENT_ID = "recent"
        private const val QUEUE_ID = "queue"
        private const val PODCASTS_ID = "podcasts"
        private const val PODCAST_PREFIX = "podcast/"
    }
}
