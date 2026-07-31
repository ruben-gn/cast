package cast.android.domain.repository.impl

import cast.android.domain.cache.LatestCache
import cast.android.domain.repository.DownloadRepository
import cast.android.domain.repository.EpisodeRepository
import cast.android.network.CastApiService
import cast.android.network.orThrow
import cast.api.EpisodeDetailDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EpisodeRepositoryImpl @Inject constructor(
    private val api: CastApiService,
    private val downloadRepository: DownloadRepository,
) : EpisodeRepository {

    private val recentCache = LatestCache<List<EpisodeDetailDto>>()

    override fun cachedRecentEpisodes(): List<EpisodeDetailDto>? = recentCache.latest

    override suspend fun getRecentEpisodes(): List<EpisodeDetailDto> =
        api.getRecentEpisodes().also(recentCache::put)

    override suspend fun getEpisode(episodeId: String): EpisodeDetailDto = api.getEpisode(episodeId)

    // Toggling played changes which episodes belong in "recent". Marking it stale (clear, don't
    // patch) means the next revisit cold-loads that one screen rather than seeding a list that
    // briefly flashes the just-played episode back before the background refresh removes it.
    override suspend fun setPlayed(episodeId: String, played: Boolean) {
        if (played) api.markPlayed(episodeId).orThrow() else api.markUnplayed(episodeId).orThrow()
        recentCache.clear()
        if (played) downloadRepository.remove(episodeId)
    }
}
