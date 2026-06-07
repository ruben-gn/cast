package application.usecase

import playback.core.usecase.RemovePlaybackStates
import podcast.core.models.PodcastId
import podcast.core.usecase.DeletePodcast
import podcast.core.usecase.ListEpisodes
import queue.core.usecase.DequeueEpisodes

/**
 * Removes a podcast from the catalog and cleans up the traces it leaves in
 * other bounded contexts: playback progress and the queue both key off
 * episode ids with no foreign key, so they must be cleared explicitly.
 */
class RemovePodcast(
    private val listEpisodes: ListEpisodes,
    private val deletePodcast: DeletePodcast,
    private val removePlaybackStates: RemovePlaybackStates,
    private val dequeueEpisodes: DequeueEpisodes,
) {
    suspend operator fun invoke(id: PodcastId): Boolean {
        val episodeIds = listEpisodes(id).map { it.id }
        if (!deletePodcast(id)) return false
        removePlaybackStates(episodeIds)
        dequeueEpisodes(episodeIds)
        return true
    }
}
