package application.usecase

import application.model.EpisodeInContext
import playback.core.usecase.GetPlaybackState
import podcast.core.usecase.FindEpisode
import podcast.core.usecase.GetPodcast
import shared.model.EpisodeId

class GetEpisodeDetail(
    private val findEpisode: FindEpisode,
    private val getPlaybackState: GetPlaybackState,
    private val getPodcast: GetPodcast,
) {
    suspend operator fun invoke(episodeId: EpisodeId): EpisodeInContext? {
        val episode = findEpisode(episodeId) ?: return null
        val podcast = getPodcast(episode.podcastId) ?: return null
        val playback = getPlaybackState(episodeId)
        return EpisodeInContext(
            episode = episode,
            progressMs = playback.progressMs,
            played = playback.played,
            podcastName = podcast.name,
            podcastImage = podcast.image,
            seriesName = null,
        )
    }
}
