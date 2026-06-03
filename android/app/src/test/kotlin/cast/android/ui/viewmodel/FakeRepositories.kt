package cast.android.ui.viewmodel

import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.QueueRepository
import cast.api.EpisodeDetailDto

/** Minimal [EpisodeRepository] fake for ViewModel seeding tests. */
class FakeEpisodeRepository(
    private val cachedRecent: List<EpisodeDetailDto>? = null,
) : EpisodeRepository {
    override fun cachedRecentEpisodes(): List<EpisodeDetailDto>? = cachedRecent
    override suspend fun getRecentEpisodes(): List<EpisodeDetailDto> = cachedRecent ?: emptyList()
    override suspend fun getEpisode(episodeId: String): EpisodeDetailDto = TODO()
    override suspend fun markPlayed(episodeId: String) {}
    override suspend fun markUnplayed(episodeId: String) {}
}

/** Minimal [QueueRepository] fake for ViewModel seeding tests. */
class FakeQueueRepository(
    private val cached: List<EpisodeDetailDto>? = null,
) : QueueRepository {
    override fun cachedQueue(): List<EpisodeDetailDto>? = cached
    override suspend fun getQueue(): List<EpisodeDetailDto> = cached ?: emptyList()
    override suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto> = cached ?: emptyList()
    override suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto> = cached ?: emptyList()
    override suspend fun reorderQueue(episodeIds: List<String>): List<EpisodeDetailDto> = cached ?: emptyList()
}
