package cast.android.domain.repository

import cast.api.EpisodeDetailDto

interface EpisodeRepository {
    fun cachedRecentEpisodes(): List<EpisodeDetailDto>?
    suspend fun getRecentEpisodes(): List<EpisodeDetailDto>
    suspend fun getEpisode(episodeId: String): EpisodeDetailDto
    suspend fun markPlayed(episodeId: String)
    suspend fun markUnplayed(episodeId: String)
}
