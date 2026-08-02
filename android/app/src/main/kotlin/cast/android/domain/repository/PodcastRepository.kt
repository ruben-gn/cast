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
    suspend fun removePodcast(podcastId: String)
    suspend fun importOpml(file: MultipartBody.Part)
    suspend fun setListening(podcastId: String, listening: Boolean)
    suspend fun createSeriesRule(podcastId: String, name: String)
    suspend fun deleteSeriesRule(podcastId: String, name: String)
}
