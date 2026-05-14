package application.usecase

import application.model.EpisodeWithPlayback
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import playback.core.usecase.GetPlaybackStates
import podcast.core.ports.PodcastCatalog
import queue.core.usecase.GetQueue

class GetQueueDetail(
    private val getQueue: GetQueue,
    private val catalog: PodcastCatalog,
    private val getPlaybackStates: GetPlaybackStates,
) {
    suspend operator fun invoke(): List<EpisodeWithPlayback> = coroutineScope {
        val queue = getQueue()
        val episodes = queue.episodeIds
            .map { id -> async { catalog.findEpisodeById(id) } }
            .mapNotNull { it.await() }
        val states = getPlaybackStates(episodes.map { it.id })
        episodes.map { episode ->
            val state = states[episode.id]
            EpisodeWithPlayback(episode, state?.progressMs ?: 0, state?.played ?: false)
        }
    }
}
