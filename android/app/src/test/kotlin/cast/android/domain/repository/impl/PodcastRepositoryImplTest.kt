package cast.android.domain.repository.impl

import cast.android.network.FakeCastApiService
import cast.api.PodcastSummaryDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastRepositoryImplTest {

    private fun podcast(id: String) = PodcastSummaryDto(
        id = id,
        url = "https://example.test/$id.xml",
        name = "Podcast $id",
        image = "",
        created = "",
        updated = "",
    )

    @Test
    fun `cachedPodcasts is null before any fetch`() = runTest {
        val repo = PodcastRepositoryImpl(FakeCastApiService(podcasts = listOf(podcast("1"))))
        assertNull(repo.cachedPodcasts())
    }

    @Test
    fun `cachedPodcasts returns the last fetched list`() = runTest {
        val repo = PodcastRepositoryImpl(FakeCastApiService(podcasts = listOf(podcast("1"), podcast("2"))))
        repo.listPodcasts()
        assertEquals(listOf("1", "2"), repo.cachedPodcasts()?.map { it.id })
    }
}
