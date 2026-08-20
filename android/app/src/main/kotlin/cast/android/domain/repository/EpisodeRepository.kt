package cast.android.domain.repository

import cast.api.EpisodeDetailDto

interface EpisodeRepository {
    fun cachedRecentEpisodes(): List<EpisodeDetailDto>?
    fun invalidateRecentCache()
    suspend fun getRecentEpisodes(): List<EpisodeDetailDto>
    suspend fun getEpisode(episodeId: String): EpisodeDetailDto
    suspend fun setPlayed(episodeId: String, played: Boolean)
}
