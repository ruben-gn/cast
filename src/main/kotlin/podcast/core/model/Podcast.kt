package podcast.core.model

import java.time.Instant

data class Podcast(
    val id: String,
    val url: String,
    val name: String,
    val image: String,
    val createdAt: Instant
)
