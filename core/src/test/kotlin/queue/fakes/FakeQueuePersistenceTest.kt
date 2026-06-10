package queue.fakes

import io.kotest.core.spec.style.DescribeSpec
import queue.core.ports.queuePersistenceContract

class FakeQueuePersistenceTest : DescribeSpec({
    lateinit var persistence: FakeQueuePersistence
    beforeEach { persistence = FakeQueuePersistence() }
    include(queuePersistenceContract { persistence })
})
