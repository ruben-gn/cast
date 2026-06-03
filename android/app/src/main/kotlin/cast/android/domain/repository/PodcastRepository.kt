package cast.android.domain.repository

import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import okhttp3.MultipartBody

interface PodcastRepository {
    fun cachedPodcasts(): List<PodcastSummaryDto>?
    suspend fun listPodcasts(): List<PodcastSummaryDto>
    suspend fun getPodcast(id: String): PodcastDetailDto
    suspend fun addPodcast(feedUrl: String): PodcastDetailDto
    suspend fun markAllPlayed(podcastId: String)
    suspend fun importOpml(file: MultipartBody.Part)
}
