package queue.fakes

import queue.core.model.Queue
import queue.core.ports.QueuePersistence

class FakeQueuePersistence : QueuePersistence {

    private var storage: Queue = Queue(emptyList())

    override suspend fun get(): Queue = storage

    override suspend fun save(queue: Queue) {
        storage = queue
    }
}