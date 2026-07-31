package cast.android.service

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressOutboxFlusherTest {

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
        val sent = mutableListOf<Pair<String, String?>>()
        val flusher = ProgressOutboxFlusher(store) { message, coalesceKey -> sent += message to coalesceKey; true }

        flusher.flush()

        assertEquals(
            listOf(
                """{"type":"update","episodeId":"ep1","progressMs":5000,"updatedAt":1000}""" to "ep1",
                """{"type":"update","episodeId":"ep2","progressMs":9000,"updatedAt":2000}""" to "ep2",
            ),
            sent,
        )
    }

    @Test
    fun `sends ended for each pending-ended id and clears the flag when send succeeds`() = runTest {
        val store = FakeProgressStore(
            pending = PendingSync(progress = emptyList(), endedEpisodeIds = listOf("ep1", "ep2")),
        )
        val flusher = ProgressOutboxFlusher(store) { _, _ -> true }

        flusher.flush()

        assertEquals(setOf("ep1", "ep2"), store.clearedEnded)
    }

    @Test
    fun `keeps the ended flag when send fails`() = runTest {
        val store = FakeProgressStore(
            pending = PendingSync(progress = emptyList(), endedEpisodeIds = listOf("ep1")),
        )
        val flusher = ProgressOutboxFlusher(store) { _, _ -> false }

        flusher.flush()

        assertTrue(store.clearedEnded.isEmpty())
    }

    @Test
    fun `sends nothing when the store has nothing pending`() = runTest {
        val store = FakeProgressStore(pending = PendingSync(progress = emptyList(), endedEpisodeIds = emptyList()))
        var sendCount = 0
        val flusher = ProgressOutboxFlusher(store) { _, _ -> sendCount++; true }

        flusher.flush()

        assertEquals(0, sendCount)
    }

    private class FakeProgressStore(private val pending: PendingSync) : PlaybackProgressStore {
        val clearedEnded = mutableSetOf<String>()
        override suspend fun cachedProgressMs(episodeId: String): Long? = null
        override fun cacheProgress(episodeId: String, progressMs: Long, atMillis: Long) {}
        override fun clearCachedProgress(episodeId: String) {}
        override fun markEndedPending(episodeId: String) {}
        override fun clearEndedPending(episodeId: String) { clearedEnded += episodeId }
        override suspend fun pendingSync(): PendingSync = pending
        override fun rememberLastEpisode(episodeId: String) {}
        override fun clearLastEpisode() {}
    }
}
