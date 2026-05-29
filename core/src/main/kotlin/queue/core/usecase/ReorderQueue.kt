package queue.core.usecase

import queue.core.model.Queue
import queue.core.ports.QueuePersistence
import shared.model.EpisodeId

class ReorderQueue(private val queues: QueuePersistence) {
    suspend operator fun invoke(newOrder: List<EpisodeId>): Queue {
        val current = queues.get().episodeIds.toSet()
        val ordered = newOrder.filter { it in current }
        val remaining = queues.get().episodeIds.filter { it !in ordered }
        return Queue(ordered + remaining).also { queues.save(it) }
    }
}
