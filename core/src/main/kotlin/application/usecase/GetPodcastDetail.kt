package application.usecase

import application.model.EpisodeInContext
import application.model.PodcastWithPlayback
import playback.core.usecase.GetPlaybackStates
import podcast.core.matchSeriesName
import podcast.core.models.PodcastId
import podcast.core.usecase.GetPodcast
import podcast.core.usecase.ListEpisodes
import podcast.core.usecase.ListSeriesRules
import settings.core.usecase.GetSettings

class GetPodcastDetail(
    private val getPodcast: GetPodcast,
    private val listEpisodes: ListEpisodes,
    private val getPlaybackStates: GetPlaybackStates,
    private val getSettings: GetSettings,
    private val listSeriesRules: ListSeriesRules,
) {
    suspend operator fun invoke(id: PodcastId): PodcastWithPlayback? {
        val podcast = getPodcast(id) ?: return null
        val hidePlayed = getSettings().hidePlayed
        val episodes = listEpisodes(id)
        val states = getPlaybackStates(episodes.map { it.id })
        val rules = listSeriesRules()
        return PodcastWithPlayback(
            podcast = podcast,
            episodes = episodes.mapNotNull { episode ->
                val state = states[episode.id]
                val played = state?.played ?: false
                if (hidePlayed && played) return@mapNotNull null
                EpisodeInContext(
                    episode = episode,
                    progressMs = state?.progressMs ?: 0,
                    played = played,
                    podcastName = podcast.name,
                    podcastImage = podcast.image,
                    seriesName = rules.matchSeriesName(episode.podcastId, episode.title),
                )
            }
        )
    }
}
