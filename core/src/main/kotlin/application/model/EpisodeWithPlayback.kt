package application.model

import podcast.core.models.Episode

data class EpisodeWithPlayback(
    val episode: Episode,
    val progressMs: Long,
    val played: Boolean,
)
