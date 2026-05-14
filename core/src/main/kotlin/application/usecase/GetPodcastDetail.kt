package application.usecase

import application.model.EpisodeWithPlayback
import application.model.PodcastWithPlayback
import playback.core.usecase.GetPlaybackStates
import podcast.core.models.PodcastId
import podcast.core.usecase.GetPodcast
import podcast.core.usecase.ListEpisodes

class GetPodcastDetail(
    private val getPodcast: GetPodcast,
    private val listEpisodes: ListEpisodes,
    private val getPlaybackStates: GetPlaybackStates,
) {
    suspend operator fun invoke(id: PodcastId): PodcastWithPlayback? {
        val podcast = getPodcast(id) ?: return null
        val episodes = listEpisodes(id)
        val states = getPlaybackStates(episodes.map { it.id })
        return PodcastWithPlayback(
            podcast = podcast,
            episodes = episodes.map { episode ->
                val state = states[episode.id]
                EpisodeWithPlayback(
                    episode = episode,
                    progressMs = state?.progressMs ?: 0,
                    played = state?.played ?: false,
                )
            }
        )
    }
}
