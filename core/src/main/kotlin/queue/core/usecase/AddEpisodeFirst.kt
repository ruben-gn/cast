package queue.core.usecase

import queue.core.model.Queue
import queue.core.ports.QueuePersistence
import shared.model.EpisodeId

class AddEpisodeFirst(private val queues: QueuePersistence) {
    suspend operator fun invoke(episodeId: EpisodeId) =
        queues.get().episodeIds
            .let { ids -> setOf(episodeId) + ids }
            .let(::Queue)
            .also { queues.save(it) }

}