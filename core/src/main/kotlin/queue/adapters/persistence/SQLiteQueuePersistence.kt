package queue.adapters.persistence

import configuration.ConnectionProvider
import queue.core.model.Queue
import queue.core.ports.QueuePersistence

class SQLiteQueuePersistence(private val db: ConnectionProvider) : QueuePersistence {
    override suspend fun get(): Queue {
        TODO("Not yet implemented")
    }

    override suspend fun save(queue: Queue) {
        TODO("Not yet implemented")
    }
}