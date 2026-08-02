package application.usecase

import application.model.EpisodeInContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import playback.core.usecase.GetPlaybackStates
import podcast.core.usecase.FindEpisode
import podcast.core.usecase.ListPodcasts
import queue.core.usecase.GetQueue
import settings.core.usecase.GetSettings

class GetQueueDetail(
    private val getQueue: GetQueue,
    private val findEpisode: FindEpisode,
    private val getPlaybackStates: GetPlaybackStates,
    private val getSettings: GetSettings,
    private val listPodcasts: ListPodcasts,
) {
    suspend operator fun invoke(): List<EpisodeInContext> = coroutineScope {
        val hidePlayed = getSettings().hidePlayed
        val queue = getQueue()
        val episodes = queue.episodeIds
            .map { id -> async { findEpisode(id) } }
            .mapNotNull { it.await() }
        val states = getPlaybackStates(episodes.map { it.id })
        val podcasts = listPodcasts().associateBy { it.id }
        episodes.mapNotNull { episode ->
            val state = states[episode.id]
            val played = state?.played ?: false
            if (hidePlayed && played) return@mapNotNull null
            val podcast = podcasts[episode.podcastId] ?: return@mapNotNull null
            EpisodeInContext(
                episode = episode,
                progressMs = state?.progressMs ?: 0,
                played = played,
                podcastName = podcast.name,
                podcastImage = podcast.image,
                seriesName = null,
            )
        }
    }
}
