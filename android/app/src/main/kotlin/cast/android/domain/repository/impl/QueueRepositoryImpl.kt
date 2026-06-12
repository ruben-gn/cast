package cast.android.domain.repository.impl

import cast.android.domain.cache.LatestCache
import cast.android.domain.repository.QueueRepository
import cast.android.network.CastApiService
import cast.api.EpisodeDetailDto
import cast.api.ReorderQueueRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepositoryImpl @Inject constructor(
    private val api: CastApiService,
) : QueueRepository {

    private val queueCache = LatestCache<List<EpisodeDetailDto>>()
    private val _queueIds = MutableStateFlow<List<String>>(emptyList())
    override val queueIds: StateFlow<List<String>> = _queueIds.asStateFlow()

    override fun cachedQueue(): List<EpisodeDetailDto>? = queueCache.latest

    override suspend fun getQueue(): List<EpisodeDetailDto> =
        api.getQueue().also { updateCache(it) }

    override suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto> =
        api.addToQueue(episodeId).also { updateCache(it) }

    override suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto> =
        api.removeFromQueue(episodeId).also { updateCache(it) }

    override suspend fun reorderQueue(episodeIds: List<String>): List<EpisodeDetailDto> =
        api.reorderQueue(ReorderQueueRequest(episodeIds)).also { updateCache(it) }

    private fun updateCache(episodes: List<EpisodeDetailDto>) {
        queueCache.put(episodes)
        _queueIds.value = episodes.map { it.id }
    }
}
