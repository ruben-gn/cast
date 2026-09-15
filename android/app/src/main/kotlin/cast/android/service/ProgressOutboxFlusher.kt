package cast.android.service

import cast.api.EpisodeEndedMessage
import cast.api.PlaybackClientMessage
import cast.api.UpdateProgressMessage

/**
 * Replays progress and mark-played events recorded while offline. Updates go before endeds so a
 * finished episode ends up `played` regardless of both being pending; a mid-flush send failure
 * loses nothing since ended flags and progress entries stay set for the next reconnect.
 */
class ProgressOutboxFlusher(
    private val store: PlaybackProgressStore,
    private val sendWs: (message: PlaybackClientMessage, coalesceKey: String?) -> Boolean,
) {
    suspend fun flush() {
        val pending = store.pendingSync()
        for (p in pending.progress) {
            sendWs(
                UpdateProgressMessage(episodeId = p.episodeId, progressMs = p.progressMs, updatedAt = p.atMillis),
                p.episodeId,
            )
        }
        for (episodeId in pending.endedEpisodeIds) {
            if (sendWs(EpisodeEndedMessage(episodeId), null))
                store.clearEndedPending(episodeId)
        }
    }
}
