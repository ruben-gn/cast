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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
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
import cast.android.domain.repository.DownloadRepository
import cast.android.domain.repository.DownloadStatus
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.PodcastRepository
import cast.android.domain.repository.QueueRepository
import cast.android.domain.repository.impl.DownloadTimestampStore
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
    @Inject lateinit var downloadRepository: DownloadRepository
    @Inject lateinit var downloadTimestampStore: DownloadTimestampStore
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
    private var resuming = false
    private lateinit var progressStore: PlaybackProgressStore

    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setSeekBackIncrementMs(SEEK_BACK_MS)
            .setSeekForwardIncrementMs(SEEK_FORWARD_MS)
            .build()
        // Wrap so controllers (Android Auto, notification, headphones) never see skip-to-next/prev:
        // the queue is a playlist that auto-advances internally, but only rewind/fast-forward are
        // exposed. Auto-advance is internal to ExoPlayer and unaffected by hiding these commands.
        val player = NoSkipPlayer(exoPlayer)

        progressStore = DataStorePlaybackProgressStore(dataStore, libraryScope)
        val listener = QueuePlaybackListener(
            player = player,
            scope = serviceScope,
            queue = queueRepository,
            store = progressStore,
            sendWs = ::sendWs,
            toMediaItem = { playableItem(it) },
            onWidgetUpdate = ::pushWidgetState,
            onEpisodeFinished = ::notifyBrowseTreeChanged,
            startProgressSync = ::startProgressSync,
            stopProgressSync = ::stopProgressSync,
        )
        exoPlayer.addListener(listener)

        val sessionActivity = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )

        // Android Auto shows skip-to-prev/next by default, which do nothing during single-episode
        // playback. Put rewind/fast-forward in those slots instead. Using player commands (not custom
        // session commands) wires the seek automatically. There is no 20s forward icon, so the
        // forward slot uses the generic skip-forward glyph.
        val rewindButton = CommandButton.Builder(CommandButton.ICON_SKIP_BACK_15)
            .setDisplayName("Rewind")
            .setPlayerCommand(Player.COMMAND_SEEK_BACK)
            .setSlots(CommandButton.SLOT_BACK)
            .build()
        val fastForwardButton = CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
            .setDisplayName("Fast forward")
            .setPlayerCommand(Player.COMMAND_SEEK_FORWARD)
            .setSlots(CommandButton.SLOT_FORWARD)
            .build()

        mediaSession = MediaLibrarySession.Builder(this, player, LibraryCallback())
            .setSessionActivity(sessionActivity)
            .setMediaButtonPreferences(ImmutableList.of(rewindButton, fastForwardButton))
            .build()

        serviceScope.launch {
            playbackWebSocketClient.states.collect { state -> listener.onServerState(state) }
        }

        // Backend queue is the source of truth: mirror it into the player's playlist tail whenever
        // it changes (add/remove/reorder from any screen).
        serviceScope.launch {
            queueRepository.queueIds.collect { listener.reconcileQueueTail() }
        }

        // Replay offline progress/mark-played on every (re)connect, including the very first open —
        // this collector must be running before connect() below for that to hold, since the
        // SharedFlow has no replay.
        val flusher = ProgressOutboxFlusher(progressStore, ::sendWs)
        serviceScope.launch {
            playbackWebSocketClient.opened.collect { flusher.flush() }
        }

        playbackWebSocketClient.connect()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = mediaSession?.player
        // DIAGNOSTIC: confirms the service actually started on a widget tap. If a widget Play press
        // after a long app-close produces NO line here, the OS blocked the background startService()
        // and the fix belongs at the widget/start layer (not inside onStartCommand).
        Log.d(TAG, "onStartCommand: action=${intent?.action} playerNull=${player == null} " +
            "isPlaying=${player?.isPlaying} mediaItemCount=${player?.mediaItemCount}")
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
            MediaSession.MediaItemsWithStartPosition(
                listOf(playableItem(episode, completionExtras(episode))), 0, episode.progressMs,
            )
        }

        // Prev/next media keys (headphones, Android Auto, steering wheel) default to
        // seekToPrevious()/seekToNext(). During single-episode playback there is no adjacent
        // item, so those no-op. Reroute them to seekBack()/seekForward() to match the in-app
        // rewind/fast-forward buttons.
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
            if (keyEvent?.action == KeyEvent.ACTION_DOWN) {
                when (keyEvent.keyCode) {
                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> { session.player.seekBack(); return true }
                    KeyEvent.KEYCODE_MEDIA_NEXT -> { session.player.seekForward(); return true }
                }
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
     * item, and the System UI resumption notification on the resumed one. Both read these off the
     * media description; partial progress also needs a percentage.
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

    /**
     * A browser caches the children it subscribed to and only re-queries them when told, so an
     * episode finishing during an Android Auto session leaves it listed under Recent (the backend
     * drops played episodes from /recent) and under Queue (it was consumed). Item counts are
     * unknown here — the browser re-queries anyway — so pass MAX_VALUE per the API docs.
     */
    private fun notifyBrowseTreeChanged() {
        val session = mediaSession ?: return
        listOf(RECENT_ROOT_ID, RECENT_ID, QUEUE_ID).forEach {
            session.notifyChildrenChanged(it, Int.MAX_VALUE, null)
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

    private fun startProgressSync(episodeId: String) {
        progressJob?.cancel()
        progressJob = serviceScope.launch {
            while (isActive) {
                delay(1_000)
                val progressMs = mediaSession?.player?.currentPosition ?: break
                val now = System.currentTimeMillis()
                progressStore.cacheProgress(episodeId, progressMs, now)
                if (downloadRepository.statuses.value[episodeId] == DownloadStatus.DOWNLOADED) {
                    downloadTimestampStore.markPlayed(episodeId)
                }
                sendWs(
                    """{"type":"update","episodeId":"$episodeId","progressMs":$progressMs,"updatedAt":$now}""",
                    coalesceKey = episodeId,
                )
            }
        }
    }

    private fun stopProgressSync() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun sendWs(message: String, coalesceKey: String? = null): Boolean {
        Log.d(TAG, "sendWs: $message")
        return playbackWebSocketClient.send(message, coalesceKey)
    }

    /**
     * Forwarding player that hides skip-to-next/previous from all controllers (Android Auto,
     * notification, headphones), leaving only rewind/fast-forward. The underlying ExoPlayer still
     * auto-advances through the playlist at end-of-item — that is internal to playback and not
     * gated by these (controller-facing) commands.
     */
    private class NoSkipPlayer(player: Player) : ForwardingPlayer(player) {
        override fun getAvailableCommands(): Player.Commands =
            super.getAvailableCommands().buildUpon()
                .remove(Player.COMMAND_SEEK_TO_NEXT)
                .remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                .remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

        override fun isCommandAvailable(command: Int): Boolean =
            command != Player.COMMAND_SEEK_TO_NEXT &&
            command != Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM &&
            command != Player.COMMAND_SEEK_TO_PREVIOUS &&
            command != Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM &&
            super.isCommandAvailable(command)
    }

    companion object {
        private const val TAG = "Cast/Playback"
        private const val SEEK_BACK_MS = 15_000L
        private const val SEEK_FORWARD_MS = 20_000L
        const val ACTION_PLAY_PAUSE = "cast.android.widget.PLAY_PAUSE"
        const val ACTION_SEEK_BACK = "cast.android.widget.SEEK_BACK"
        const val ACTION_SEEK_FORWARD = "cast.android.widget.SEEK_FORWARD"

        // Read by PlayerViewModel to restore the remembered episode (paused) when the app opens
        // with an empty player — e.g. cold-started from a widget tap.
        internal val LAST_EPISODE_ID = stringPreferencesKey("last_episode_id")

        private const val ROOT_ID = "root"
        private const val RECENT_ROOT_ID = "recent_root"
        private const val RECENT_ID = "recent"
        private const val QUEUE_ID = "queue"
        private const val PODCASTS_ID = "podcasts"
        private const val PODCAST_PREFIX = "podcast/"
    }
}
