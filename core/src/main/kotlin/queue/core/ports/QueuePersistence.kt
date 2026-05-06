package queue.core.ports

import queue.core.model.Queue

interface QueuePersistence {
    suspend fun get(): Queue
    suspend fun save(queue: Queue)
}