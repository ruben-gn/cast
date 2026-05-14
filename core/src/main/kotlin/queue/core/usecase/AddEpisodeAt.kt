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
            .minus(episodeId)
            .let { ids -> ids.take(position) + episodeId + ids.drop(position) }
            .let(::Queue)
            .also { queues.save(it) }

}