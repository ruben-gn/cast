package cast.android.domain.repository.impl

import cast.android.network.FakeCastApiService
import cast.android.network.episode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRepositoryImplTest {

    @Test
    fun `cachedQueue is null before any fetch`() = runTest {
        val repo = QueueRepositoryImpl(FakeCastApiService(queue = listOf(episode("1"))))
        assertNull(repo.cachedQueue())
    }

    @Test
    fun `cachedQueue returns the last fetched queue`() = runTest {
        val repo = QueueRepositoryImpl(FakeCastApiService(queue = listOf(episode("1"), episode("2"))))
        repo.getQueue()
        assertEquals(listOf("1", "2"), repo.cachedQueue()?.map { it.id })
    }

    @Test
    fun `removeFromQueue updates the cache`() = runTest {
        val repo = QueueRepositoryImpl(
            FakeCastApiService(
                queue = listOf(episode("1"), episode("2")),
                queueAfterMutation = listOf(episode("2")),
            )
        )
        repo.getQueue()
        repo.removeFromQueue("1")
        assertEquals(listOf("2"), repo.cachedQueue()?.map { it.id })
    }
}
