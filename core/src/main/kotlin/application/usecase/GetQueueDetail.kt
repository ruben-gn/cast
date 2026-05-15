package application.usecase

import application.model.EpisodeWithPlayback
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import playback.core.usecase.GetPlaybackStates
import podcast.core.ports.PodcastCatalog
import queue.core.usecase.GetQueue
import settings.core.usecase.GetSettings

class GetQueueDetail(
    private val getQueue: GetQueue,
    private val catalog: PodcastCatalog,
    private val getPlaybackStates: GetPlaybackStates,
    private val getSettings: GetSettings,
) {
    suspend operator fun invoke(): List<EpisodeWithPlayback> = coroutineScope {
        val hidePlayed = getSettings().hidePlayed
        val queue = getQueue()
        val episodes = queue.episodeIds
            .map { id -> async { catalog.findEpisodeById(id) } }
            .mapNotNull { it.await() }
        val states = getPlaybackStates(episodes.map { it.id })
        episodes.mapNotNull { episode ->
            val state = states[episode.id]
            val played = state?.played ?: false
            if (hidePlayed && played) return@mapNotNull null
            EpisodeWithPlayback(episode, state?.progressMs ?: 0, played)
        }
    }
}
