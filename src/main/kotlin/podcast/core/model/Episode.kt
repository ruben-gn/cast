package podcast.core.model

import java.time.Instant
import kotlin.time.Duration

data class Episode(
    val id: String,
    val podcastId: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: Duration?,
    val publishedAt: Instant?
)