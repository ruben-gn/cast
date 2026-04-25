package podcast.core.models

import java.time.Instant

data class Podcast(
    val id: PodcastId,
    val url: FeedUrl,
    val name: String,
    val image: String,

    val created: Instant,
    val updated: Instant
)

@JvmInline
value class PodcastId(val value: String) {
    override fun toString() = value
}

@JvmInline
value class FeedUrl(val value: String) {
    override fun toString() = value
}
