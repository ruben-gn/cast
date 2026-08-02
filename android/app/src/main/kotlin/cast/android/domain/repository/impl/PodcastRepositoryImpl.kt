package cast.android.domain.repository.impl

import cast.android.domain.cache.LatestCache
import cast.android.domain.repository.PodcastRepository
import cast.android.network.CastApiService
import cast.android.network.orThrow
import cast.api.AddPodcastRequest
import cast.api.CreateSeriesRuleRequest
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

    // Both addPodcast and importOpml grow the subscription list, so drop the cached summaries to
    // force a fresh load rather than briefly showing a list missing the just-added podcast(s).
    // markAllPlayed is not invalidated: PodcastSummaryDto carries no played state, so the cached
    // summaries stay correct and a reload would be wasted work.
    override suspend fun addPodcast(feedUrl: String): PodcastDetailDto =
        api.addPodcast(AddPodcastRequest(feedUrl)).also { podcastsCache.clear() }

    override suspend fun markAllPlayed(podcastId: String) {
        api.markAllPodcastPlayed(podcastId).orThrow()
    }

    // Removal shrinks the subscription list, so drop the cached summaries to avoid
    // briefly showing the just-removed podcast on the next list load.
    override suspend fun removePodcast(podcastId: String) {
        api.removePodcast(podcastId).orThrow()
        podcastsCache.clear()
    }

    override suspend fun importOpml(file: MultipartBody.Part) {
        api.importOpml(file).orThrow()
        podcastsCache.clear()
    }

    override suspend fun setListening(podcastId: String, listening: Boolean) {
        if (listening) {
            api.startListening(podcastId).orThrow()
        } else {
            api.stopListening(podcastId).orThrow()
        }
        podcastsCache.clear()
    }

    override suspend fun createSeriesRule(podcastId: String, name: String) {
        api.createSeriesRule(podcastId, CreateSeriesRuleRequest(name)).orThrow()
    }

    override suspend fun deleteSeriesRule(podcastId: String, name: String) {
        api.deleteSeriesRule(podcastId, name).orThrow()
    }
}
