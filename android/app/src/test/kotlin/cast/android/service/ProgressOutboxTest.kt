package cast.android.service

import cast.api.EpisodeEndedAckMessage
import cast.api.EpisodeEndedMessage
import cast.api.PlaybackClientMessage
import cast.api.ProgressAckMessage
import cast.api.UpdateProgressMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressOutboxTest {

    @Test
    fun `sends a timestamped update per pending progress entry, coalesced by episode id`() = runTest {
        val store = FakeProgressStore(
            pending = PendingSync(
                progress = listOf(
                    PendingProgress("ep1", 5_000L, 1_000L),
                    PendingProgress("ep2", 9_000L, 2_000L),
                ),
                endedEpisodeIds = emptyList(),
            ),
        )
        val sent = mutableListOf<Pair<PlaybackClientMessage, String?>>()
        val outbox = ProgressOutbox(store) { message, coalesceKey -> sent += message to coalesceKey; true }

        outbox.flush()

        assertEquals(
            listOf(
                UpdateProgressMessage("ep1", 5000, 1000) to "ep1",
                UpdateProgressMessage("ep2", 9000, 2000) to "ep2",
            ),
            sent,
        )
    }

    @Test
    fun `sends ended for each pending-ended id`() = runTest {
        val store = FakeProgressStore(
            pending = PendingSync(progress = emptyList(), endedEpisodeIds = listOf("ep1", "ep2")),
        )
        val sent = mutableListOf<PlaybackClientMessage>()
        val outbox = ProgressOutbox(store) { message, _ -> sent += message; true }

        outbox.flush()

        assertEquals(listOf(EpisodeEndedMessage("ep1"), EpisodeEndedMessage("ep2")), sent)
    }

    @Test
    fun `keeps everything pending until the server acknowledges it`() = runTest {
        val store = FakeProgressStore(
            pending = PendingSync(
                progress = listOf(PendingProgress("ep1", 5_000L, 1_000L)),
                endedEpisodeIds = listOf("ep1"),
            ),
        )
        val outbox = ProgressOutbox(store) { _, _ -> true }

        outbox.flush()

        assertTrue(store.clearedProgress.isEmpty())
        assertTrue(store.clearedEnded.isEmpty())
    }

    @Test
    fun `retires an entry when its ack arrives`() = runTest {
        val store = FakeProgressStore(pending = PendingSync(emptyList(), emptyList()))
        val outbox = ProgressOutbox(store) { _, _ -> true }

        outbox.onAck(ProgressAckMessage("ep1", updatedAt = 1_000L))
        outbox.onAck(EpisodeEndedAckMessage("ep2"))

        assertEquals(listOf("ep1" to 1_000L), store.clearedProgress)
        assertEquals(setOf("ep2"), store.clearedEnded)
    }

    @Test
    fun `ignores a progress ack without a timestamp, which cannot identify an entry`() = runTest {
        val store = FakeProgressStore(pending = PendingSync(emptyList(), emptyList()))
        val outbox = ProgressOutbox(store) { _, _ -> true }

        outbox.onAck(ProgressAckMessage("ep1", updatedAt = null))

        assertTrue(store.clearedProgress.isEmpty())
    }

    @Test
    fun `sends nothing when the store has nothing pending`() = runTest {
        val store = FakeProgressStore(pending = PendingSync(progress = emptyList(), endedEpisodeIds = emptyList()))
        var sendCount = 0
        val outbox = ProgressOutbox(store) { _, _ -> sendCount++; true }

        outbox.flush()

        assertEquals(0, sendCount)
    }

    private class FakeProgressStore(private val pending: PendingSync) : PlaybackProgressStore {
        val clearedEnded = mutableSetOf<String>()
        val clearedProgress = mutableListOf<Pair<String, Long>>()
        override suspend fun cachedProgressMs(episodeId: String): Long? = null
        override fun cacheProgress(episodeId: String, progressMs: Long, atMillis: Long) {}
        override fun clearCachedProgress(episodeId: String) {}
        override fun markProgressPending(episodeId: String) {}
        override fun clearProgressPending(episodeId: String, atMillis: Long) { clearedProgress += episodeId to atMillis }
        override fun markEndedPending(episodeId: String) {}
        override fun clearEndedPending(episodeId: String) { clearedEnded += episodeId }
        override suspend fun pendingSync(): PendingSync = pending
        override fun rememberLastEpisode(episodeId: String) {}
        override fun clearLastEpisode() {}
    }
}
