package podcast.core.port

import java.time.Instant

fun interface FeedInfoProvider {
    suspend fun fetch(url: String): FeedInfo
}

data class FeedInfo(
    val title: String,
    val description: String,
    val image: String,
    val episodes: List<EpisodeInfo> = emptyList()
)

data class EpisodeInfo(
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: String?,
    val publishedAt: Instant?
)