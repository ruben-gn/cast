package cast.android.service

/**
 * Replays progress and mark-played events recorded while offline. Updates go before endeds so a
 * finished episode ends up `played` regardless of both being pending; a mid-flush send failure
 * loses nothing since ended flags and progress entries stay set for the next reconnect.
 */
class ProgressOutboxFlusher(
    private val store: PlaybackProgressStore,
    private val sendWs: (message: String, coalesceKey: String?) -> Boolean,
) {
    suspend fun flush() {
        val pending = store.pendingSync()
        for (p in pending.progress) {
            sendWs(
                """{"type":"update","episodeId":"${p.episodeId}","progressMs":${p.progressMs},"updatedAt":${p.atMillis}}""",
                p.episodeId,
            )
        }
        for (episodeId in pending.endedEpisodeIds) {
            if (sendWs("""{"type":"ended","episodeId":"$episodeId"}""", null))
                store.clearEndedPending(episodeId)
        }
    }
}
