package cast.android.service

import cast.api.EpisodeEndedAckMessage
import cast.api.EpisodeEndedMessage
import cast.api.PlaybackAck
import cast.api.PlaybackClientMessage
import cast.api.ProgressAckMessage
import cast.api.UpdateProgressMessage

/**
 * Replays progress and mark-played events the server never acknowledged. Updates go before endeds
 * so a finished episode ends up `played` regardless of both being pending; nothing is retired here,
 * only in [onAck], so a message the socket accepted but never delivered is replayed next reconnect.
 */
class ProgressOutbox(
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
            sendWs(EpisodeEndedMessage(episodeId), null)
        }
    }

    fun onAck(ack: PlaybackAck) {
        when (ack) {
            // A client that doesn't timestamp its updates has no outbox to retire from.
            is ProgressAckMessage -> ack.updatedAt?.let { store.clearProgressPending(ack.episodeId, it) }
            is EpisodeEndedAckMessage -> store.clearEndedPending(ack.episodeId)
        }
    }
}
