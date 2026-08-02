package application.usecase

import application.model.EpisodeInContext
import playback.core.usecase.GetPlaybackState
import podcast.core.usecase.FindEpisode
import podcast.core.usecase.GetPodcast
import series.core.matchSeriesName
import series.core.usecase.ListSeriesRules
import shared.model.EpisodeId

class GetEpisodeDetail(
    private val findEpisode: FindEpisode,
    private val getPlaybackState: GetPlaybackState,
    private val getPodcast: GetPodcast,
    private val listSeriesRules: ListSeriesRules,
) {
    suspend operator fun invoke(episodeId: EpisodeId): EpisodeInContext? {
        val episode = findEpisode(episodeId) ?: return null
        val podcast = getPodcast(episode.podcastId) ?: return null
        val playback = getPlaybackState(episodeId)
        val rules = listSeriesRules()
        return EpisodeInContext(
            episode = episode,
            progressMs = playback.progressMs,
            played = playback.played,
            podcastName = podcast.name,
            podcastImage = podcast.image,
            seriesName = rules.matchSeriesName(episode.podcastId, episode.title),
        )
    }
}
