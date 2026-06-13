package application.model

import podcast.core.models.Episode

data class EpisodeInContext(
    val episode: Episode,
    val progressMs: Long,
    val played: Boolean,
    val podcastName: String,
    val podcastImage: String,
)
