package cast.android.domain.repository.impl

import cast.android.domain.cache.LatestCache
import cast.android.domain.repository.QueueRepository
import cast.android.network.CastApiService
import cast.api.EpisodeDetailDto
import cast.api.ReorderQueueRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepositoryImpl @Inject constructor(
    private val api: CastApiService,
) : QueueRepository {

    private val queueCache = LatestCache<List<EpisodeDetailDto>>()

    override fun cachedQueue(): List<EpisodeDetailDto>? = queueCache.latest

    override suspend fun getQueue(): List<EpisodeDetailDto> =
        api.getQueue().also(queueCache::put)

    override suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto> =
        api.addToQueue(episodeId).also(queueCache::put)

    override suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto> =
        api.removeFromQueue(episodeId).also(queueCache::put)

    override suspend fun reorderQueue(episodeIds: List<String>): List<EpisodeDetailDto> =
        api.reorderQueue(ReorderQueueRequest(episodeIds)).also(queueCache::put)
}
