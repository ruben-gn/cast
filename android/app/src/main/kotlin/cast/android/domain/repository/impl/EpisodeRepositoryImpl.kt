package cast.android.domain.repository.impl

import cast.android.domain.repository.EpisodeRepository
import cast.android.network.CastApiService
import cast.api.EpisodeDetailDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeRepositoryImpl @Inject constructor(
    private val api: CastApiService,
) : EpisodeRepository {

    override suspend fun getRecentEpisodes(): List<EpisodeDetailDto> = api.getRecentEpisodes()

    override suspend fun getEpisode(episodeId: String): EpisodeDetailDto = api.getEpisode(episodeId)

    override suspend fun markPlayed(episodeId: String) {
        api.markPlayed(episodeId)
    }

    override suspend fun markUnplayed(episodeId: String) {
        api.markUnplayed(episodeId)
    }
}
