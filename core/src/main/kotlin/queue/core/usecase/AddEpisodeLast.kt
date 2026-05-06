package queue.core.usecase

import queue.core.model.Queue
import queue.core.ports.QueuePersistence
import shared.model.EpisodeId

class AddEpisodeLast(private val queues: QueuePersistence) {
    suspend operator fun invoke(episodeId: EpisodeId) =
        queues.get().episodeIds
            .minus(episodeId)
            .let { ids -> ids + episodeId }
            .let(::Queue)
            .also { queues.save(it) }

}