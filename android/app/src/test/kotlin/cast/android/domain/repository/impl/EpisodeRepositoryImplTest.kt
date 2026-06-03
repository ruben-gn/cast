package cast.android.domain.repository.impl

import cast.android.network.FakeCastApiService
import cast.android.network.episode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeRepositoryImplTest {

    @Test
    fun `cachedRecentEpisodes is null before any fetch`() = runTest {
        val repo = EpisodeRepositoryImpl(FakeCastApiService(recent = listOf(episode("1"))))
        assertNull(repo.cachedRecentEpisodes())
    }

    @Test
    fun `cachedRecentEpisodes returns the last fetched list`() = runTest {
        val repo = EpisodeRepositoryImpl(FakeCastApiService(recent = listOf(episode("1"), episode("2"))))
        repo.getRecentEpisodes()
        assertEquals(listOf("1", "2"), repo.cachedRecentEpisodes()?.map { it.id })
    }

    @Test
    fun `markPlayed clears the recent cache`() = runTest {
        val repo = EpisodeRepositoryImpl(FakeCastApiService(recent = listOf(episode("1"))))
        repo.getRecentEpisodes()
        repo.markPlayed("1")
        assertNull(repo.cachedRecentEpisodes())
    }

    @Test
    fun `markUnplayed clears the recent cache`() = runTest {
        val repo = EpisodeRepositoryImpl(FakeCastApiService(recent = listOf(episode("1"))))
        repo.getRecentEpisodes()
        repo.markUnplayed("1")
        assertNull(repo.cachedRecentEpisodes())
    }
}
