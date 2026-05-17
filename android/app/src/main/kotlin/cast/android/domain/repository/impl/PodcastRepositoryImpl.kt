package cast.android.domain.repository.impl

import cast.android.domain.repository.PodcastRepository
import cast.android.network.CastApiService
import cast.api.AddPodcastRequest
import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepositoryImpl @Inject constructor(
    private val api: CastApiService,
) : PodcastRepository {

    override suspend fun listPodcasts(): List<PodcastSummaryDto> = api.listPodcasts()

    override suspend fun getPodcast(id: String): PodcastDetailDto = api.getPodcast(id)

    override suspend fun addPodcast(feedUrl: String): PodcastDetailDto =
        api.addPodcast(AddPodcastRequest(feedUrl))

    override suspend fun markAllPlayed(podcastId: String) {
        api.markAllPodcastPlayed(podcastId)
    }
}
