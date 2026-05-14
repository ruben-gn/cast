package application.model

import podcast.core.models.Podcast

data class PodcastWithPlayback(
    val podcast: Podcast,
    val episodes: List<EpisodeWithPlayback>,
)
