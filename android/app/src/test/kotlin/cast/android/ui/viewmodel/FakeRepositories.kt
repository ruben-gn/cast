package cast.android.ui.viewmodel

import cast.android.domain.repository.DownloadRepository
import cast.android.domain.repository.DownloadStatus
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.QueueRepository
import cast.api.EpisodeDetailDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Minimal [EpisodeRepository] fake for ViewModel seeding tests. */
class FakeEpisodeRepository(
    private val cachedRecent: List<EpisodeDetailDto>? = null,
) : EpisodeRepository {
    override fun cachedRecentEpisodes(): List<EpisodeDetailDto>? = cachedRecent
    override suspend fun getRecentEpisodes(): List<EpisodeDetailDto> = cachedRecent ?: emptyList()
    override suspend fun getEpisode(episodeId: String): EpisodeDetailDto = TODO()
    override suspend fun setPlayed(episodeId: String, played: Boolean) {}
}

/** Minimal [DownloadRepository] fake for ViewModel/repository seeding tests. */
class FakeDownloadRepository : DownloadRepository {
    override val statuses: StateFlow<Map<String, DownloadStatus>> = MutableStateFlow(emptyMap())
    override val progress: StateFlow<Map<String, Float>> = MutableStateFlow(emptyMap())
    override suspend fun downloadedEpisodes(): List<EpisodeDetailDto> = emptyList()
    override fun download(episode: EpisodeDetailDto) {}
    override fun remove(episodeId: String) {}
}

/** Minimal [QueueRepository] fake for ViewModel seeding tests. */
class FakeQueueRepository(
    private val cached: List<EpisodeDetailDto>? = null,
) : QueueRepository {
    override fun cachedQueue(): List<EpisodeDetailDto>? = cached
    override val queueIds: StateFlow<List<String>> = MutableStateFlow(cached?.map { it.id } ?: emptyList())
    override suspend fun getQueue(): List<EpisodeDetailDto> = cached ?: emptyList()
    override suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto> = cached ?: emptyList()
    override suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto> = cached ?: emptyList()
    override suspend fun reorderQueue(episodeIds: List<String>): List<EpisodeDetailDto> = cached ?: emptyList()
}
