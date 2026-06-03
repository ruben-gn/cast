package cast.android.domain.repository.impl

import cast.android.domain.cache.LatestCache
import cast.android.domain.repository.PodcastRepository
import cast.android.network.CastApiService
import cast.api.AddPodcastRequest
import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import okhttp3.MultipartBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepositoryImpl @Inject constructor(
    private val api: CastApiService,
) : PodcastRepository {

    private val podcastsCache = LatestCache<List<PodcastSummaryDto>>()

    override fun cachedPodcasts(): List<PodcastSummaryDto>? = podcastsCache.latest

    override suspend fun listPodcasts(): List<PodcastSummaryDto> =
        api.listPodcasts().also(podcastsCache::put)

    override suspend fun getPodcast(id: String): PodcastDetailDto = api.getPodcast(id)

    override suspend fun addPodcast(feedUrl: String): PodcastDetailDto =
        api.addPodcast(AddPodcastRequest(feedUrl))

    override suspend fun markAllPlayed(podcastId: String) {
        api.markAllPodcastPlayed(podcastId)
    }

    override suspend fun importOpml(file: MultipartBody.Part) {
        api.importOpml(file)
    }
}
