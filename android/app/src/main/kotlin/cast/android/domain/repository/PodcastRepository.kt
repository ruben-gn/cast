package cast.android.domain.repository

import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto

interface PodcastRepository {
    suspend fun listPodcasts(): List<PodcastSummaryDto>
    suspend fun getPodcast(id: String): PodcastDetailDto
    suspend fun addPodcast(feedUrl: String): PodcastDetailDto
    suspend fun markAllPlayed(podcastId: String)
}
