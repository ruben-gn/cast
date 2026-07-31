package cast.android.domain.repository

import cast.api.EpisodeDetailDto
import kotlinx.coroutines.flow.StateFlow

enum class DownloadStatus { DOWNLOADING, DOWNLOADED }

interface DownloadRepository {
    /** Episode id → status; ids absent from the map are not downloaded. */
    val statuses: StateFlow<Map<String, DownloadStatus>>
    /** Episode id → download progress (0f-1f); ids absent from the map have no active download. */
    val progress: StateFlow<Map<String, Float>>
    suspend fun downloadedEpisodes(): List<EpisodeDetailDto>
    fun download(episode: EpisodeDetailDto)
    fun remove(episodeId: String)
}
