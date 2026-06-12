package cast.android.domain.repository

import cast.api.EpisodeDetailDto
import kotlinx.coroutines.flow.StateFlow

interface QueueRepository {
    fun cachedQueue(): List<EpisodeDetailDto>?
    val queueIds: StateFlow<List<String>>
    suspend fun getQueue(): List<EpisodeDetailDto>
    suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto>
    suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto>
    suspend fun reorderQueue(episodeIds: List<String>): List<EpisodeDetailDto>
}
