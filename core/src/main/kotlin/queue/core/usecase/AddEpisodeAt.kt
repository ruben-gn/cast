package queue.core.usecase

import queue.core.model.Queue
import queue.core.ports.QueuePersistence
import shared.model.EpisodeId

class AddEpisodeAt(private val queues: QueuePersistence) {

    /**
     * Using a position <= 0 will enqueue the episode at the beginning of the queue.
     * Using a position > queue.size will enqueue the episode at the end of the queue.
     */
    suspend operator fun invoke(episodeId: EpisodeId, position: Int): Queue =
        queues.get().episodeIds
            // make sure we don't add the episode twice if it's already in the queue
            .minus(episodeId)
            .let { ids -> ids.take(position) + episodeId + ids.drop(position) }
            .toSet()
            .let(::Queue)
            .also { queues.save(it) }

}