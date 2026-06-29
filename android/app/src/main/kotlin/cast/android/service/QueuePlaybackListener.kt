package cast.android.service

import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import cast.android.domain.repository.QueueRepository
import cast.api.EpisodeDetailDto
import cast.api.PlaybackStateResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The playback "brain": translates ExoPlayer events into backend-sync side effects and keeps the
 * player's playlist tail mirroring the backend queue. Extracted out of [PlaybackService] (which is
 * now just the Android service shell) so it can be attached to a real test player and driven to
 * completion off-device — the regression net for "episodes must be marked played when they finish".
 *
 * The backend stays the source of truth: natural end-of-episode emits `ended`, mid-queue advance
 * drains the consumed item, and a `get`/`start` handshake re-seeks to the server's authoritative
 * position. Everything device-local here ([store], the head-start seek) is only an optimization.
 *
 * All player access is main-thread only, matching ExoPlayer's threading contract.
 */
@OptIn(UnstableApi::class)
class QueuePlaybackListener(
    private val player: Player,
    private val scope: CoroutineScope,
    private val queue: QueueRepository,
    private val store: PlaybackProgressStore,
    private val sendWs: (message: String, coalesceKey: String?) -> Unit,
    private val toMediaItem: (EpisodeDetailDto) -> MediaItem,
    private val onWidgetUpdate: (isPlaying: Boolean) -> Unit,
    private val startProgressSync: (episodeId: String) -> Unit,
    private val stopProgressSync: () -> Unit,
) : Player.Listener {

    private var currentEpisodeId: String? = null
    private var episodeStarted = false

    /**
     * Reconcile the authoritative server state for the current episode: re-seek to the server's
     * position (or 0 if already played) and emit the `start` handshake. No-op once the episode has
     * started, or for a state about a different episode. Called by [PlaybackService] from the
     * WebSocket `states` collector.
     */
    fun onServerState(state: PlaybackStateResponse) {
        Log.d(TAG, "onServerState: episodeId=${state.episodeId} currentEpisodeId=$currentEpisodeId episodeStarted=$episodeStarted progressMs=${state.progressMs} played=${state.played}")
        if (state.episodeId != currentEpisodeId || episodeStarted) return
        val serverMs = if (state.played) 0L else state.progressMs
        val currentMs = player.currentPosition
        // Only honor the server position when it's meaningfully ahead of local playback (genuine
        // cross-device progress) or the episode was played. Otherwise local is the fresher truth:
        // seeking back to the server's value would rewind by however long the `get` round-trip took,
        // since playback kept advancing while we waited for the reply.
        val startMs = if (state.played || serverMs > currentMs + RESUME_TOLERANCE_MS) {
            player.seekTo(serverMs)
            serverMs
        } else {
            currentMs
        }
        sendWs("""{"type":"start","episodeId":"${state.episodeId}","startPositionMs":$startMs}""", null)
        episodeStarted = true
    }

    /**
     * Mirror the backend queue into the player's playlist tail: keep the now-playing item (and
     * anything before it) untouched and replace everything after it with the backend queue, minus
     * the current item. Touching only the tail never interrupts playback and fires no transition,
     * so this is loop-safe against the queueIds collector. Main-thread only (player access).
     */
    fun reconcileQueueTail() {
        if (player.mediaItemCount == 0) return
        val currentIndex = player.currentMediaItemIndex
        val currentId = player.currentMediaItem?.mediaId
        val tail = (queue.cachedQueue() ?: emptyList()).filter { it.id != currentId }
        if (player.mediaItemCount > currentIndex + 1)
            player.removeMediaItems(currentIndex + 1, player.mediaItemCount)
        if (tail.isNotEmpty())
            player.addMediaItems(tail.map(toMediaItem))
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        Log.d(TAG, "onMediaItemTransition: mediaId=${mediaItem?.mediaId} reason=$reason")
        val finishedId = currentEpisodeId
        currentEpisodeId = mediaItem?.mediaId
        episodeStarted = false
        onWidgetUpdate(player.isPlaying)

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            // The previous item ran to its natural end: mark it played and drop it locally.
            if (finishedId != null) {
                sendWs("""{"type":"ended","episodeId":"$finishedId"}""", null)
                store.clearCachedProgress(finishedId)
            }
            // The new current item has been consumed from up-next: remove it from the backend
            // queue (which re-emits queueIds → reconcileQueueTail, a no-op since it's now current).
            mediaItem?.mediaId?.let { id ->
                scope.launch { runCatching { queue.removeFromQueue(id) } }
            }
        } else {
            // User-initiated/new now-playing: refresh the queue cache so the tail is populated
            // even if the Queue screen was never opened.
            scope.launch { runCatching { queue.getQueue() } }
        }

        val episodeId = currentEpisodeId ?: return
        // Head-start seek from local cache so playback doesn't jump while we fetch the server position.
        // Does NOT set episodeStarted: the WS `get` reconcile (onServerState) still re-seeks to the
        // authoritative server value when it arrives. Server stays the source of truth.
        scope.launch {
            val cached = store.cachedProgressMs(episodeId)
            if (currentEpisodeId != episodeId || episodeStarted) return@launch
            localResumePositionMs(cached, played = false)?.let { player.seekTo(it) }
        }
        // Remember it so Auto/Bluetooth can resume after the service is killed (onPlaybackResumption).
        store.rememberLastEpisode(episodeId)
        sendWs("""{"type":"get","episodeId":"$episodeId"}""", null)

        // Append the backend queue behind the new now-playing item.
        reconcileQueueTail()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        Log.d(TAG, "onPlayWhenReadyChanged: playWhenReady=$playWhenReady reason=$reason episodeStarted=$episodeStarted")
        val episodeId = currentEpisodeId ?: return
        if (playWhenReady) {
            if (episodeStarted) {
                // Resume after intentional pause: re-sync from server so webapp progress is picked up
                episodeStarted = false
                sendWs("""{"type":"get","episodeId":"$episodeId"}""", null)
            }
            startProgressSync(episodeId)
        } else {
            // Flush exact position on intentional pause so server is never stale
            val progressMs = player.currentPosition
            store.cacheProgress(episodeId, progressMs)
            sendWs("""{"type":"update","episodeId":"$episodeId","progressMs":$progressMs}""", episodeId)
            stopProgressSync()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Log.d(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
        onWidgetUpdate(isPlaying)
    }

    override fun onPlaybackStateChanged(state: Int) {
        Log.d(TAG, "onPlaybackStateChanged: state=$state")
        if (state == Player.STATE_ENDED) {
            // Fires only when the last playlist item finishes (queue exhausted). Mid-queue
            // completion is handled in onMediaItemTransition(REASON_AUTO).
            currentEpisodeId?.let {
                sendWs("""{"type":"ended","episodeId":"$it"}""", null)
                store.clearCachedProgress(it)
            }
            stopProgressSync()
            // Clear the finished episode from the player (also clears the player bar and widget
            // via onMediaItemTransition) and don't let resumption replay it.
            player.clearMediaItems()
            store.clearLastEpisode()
        }
    }

    private companion object {
        const val TAG = "Cast/Playback"
        // Slack for the `get` round-trip: only a server position further than this beyond the live
        // position counts as real cross-device progress worth seeking forward to.
        const val RESUME_TOLERANCE_MS = 3_000L
    }
}
