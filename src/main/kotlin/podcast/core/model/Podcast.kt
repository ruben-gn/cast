package podcast.core.model

import java.time.Instant

data class Podcast(
    val id: String,
    val url: String,
    val name: String,
    val image: String,
    val createdAt: Instant,
    val episodes: List<Episode>
)

data class Episode(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: String?,
    val publishedAt: Instant?
)
