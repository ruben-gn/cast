package cast.android.domain.repository.impl

import cast.android.domain.repository.QueueRepository
import cast.android.network.CastApiService
import cast.api.EpisodeDetailDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QueueRepositoryImpl @Inject constructor(
    private val api: CastApiService,
) : QueueRepository {

    override suspend fun getQueue(): List<EpisodeDetailDto> = api.getQueue()

    override suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto> =
        api.addToQueue(episodeId)

    override suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto> =
        api.removeFromQueue(episodeId)
}
