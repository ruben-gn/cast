package cast.android.domain.repository

import cast.api.EpisodeDetailDto
import kotlinx.coroutines.flow.StateFlow

enum class DownloadStatus { DOWNLOADING, DOWNLOADED }

interface DownloadRepository {
    /** Episode id → status; ids absent from the map are not downloaded. */
    val statuses: StateFlow<Map<String, DownloadStatus>>
    suspend fun downloadedEpisodes(): List<EpisodeDetailDto>
    fun download(episode: EpisodeDetailDto)
    fun remove(episodeId: String)
}
