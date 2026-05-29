package cast.android.domain.repository

import cast.api.EpisodeDetailDto

interface QueueRepository {
    suspend fun getQueue(): List<EpisodeDetailDto>
    suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto>
    suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto>
    suspend fun reorderQueue(episodeIds: List<String>): List<EpisodeDetailDto>
}
