package cast.android.ui.viewmodel

import cast.android.domain.repository.DownloadRepository
import cast.android.domain.repository.DownloadStatus
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.PodcastRepository
import cast.android.domain.repository.QueueRepository
import cast.api.EpisodeDetailDto
import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MultipartBody

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

/** Minimal [PodcastRepository] fake for ViewModel seeding tests; records series rule calls. */
class FakePodcastRepository : PodcastRepository {
    var createdSeriesRule: Pair<String, String>? = null
    var deletedSeriesRule: Pair<String, String>? = null

    override fun cachedPodcasts(): List<PodcastSummaryDto>? = null
    override suspend fun listPodcasts(): List<PodcastSummaryDto> = emptyList()
    override suspend fun getPodcast(id: String): PodcastDetailDto = TODO()
    override suspend fun addPodcast(feedUrl: String): PodcastDetailDto = TODO()
    override suspend fun markAllPlayed(podcastId: String) {}
    override suspend fun removePodcast(podcastId: String) {}
    override suspend fun importOpml(file: MultipartBody.Part) {}
    override suspend fun setListening(podcastId: String, listening: Boolean) {}

    override suspend fun createSeriesRule(podcastId: String, name: String) {
        createdSeriesRule = podcastId to name
    }

    override suspend fun deleteSeriesRule(podcastId: String, name: String) {
        deletedSeriesRule = podcastId to name
    }
}
