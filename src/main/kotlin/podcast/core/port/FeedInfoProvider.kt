package podcast.core.port

import podcast.core.model.FeedUrl
import java.time.Instant
import kotlin.time.Duration

fun interface FeedInfoProvider {
    suspend fun fetch(url: FeedUrl): FeedInfo
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
    val duration: Duration?,
    val publishedAt: Instant?
)
