package queue.core.usecase

import queue.core.ports.QueuePersistence

class GetQueue(
    private val queues: QueuePersistence
) {
    suspend operator fun invoke() = queues.get()
}