package podcast.core.model

import java.time.Instant

data class Episode(
    val id: String,
    val podcastId: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: String?,
    val publishedAt: Instant?
)
