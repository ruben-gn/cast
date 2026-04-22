package podcast.core.model

import java.time.Instant
import kotlin.time.Duration

data class Episode(
    val id: EpisodeId,
    val podcastId: PodcastId,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: Duration?,
    val publishedAt: Instant?
)

@JvmInline
value class EpisodeId(val value: String) {
    override fun toString() = value
}
