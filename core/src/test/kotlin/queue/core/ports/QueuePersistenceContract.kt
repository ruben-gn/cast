package queue.core.ports

import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.describeSpec
import io.kotest.matchers.shouldBe
import queue.core.model.Queue
import shared.model.EpisodeId

fun queuePersistenceContract(persistenceProvider: () -> QueuePersistence): TestFactory = describeSpec {

    describe("get") {
        it("returns an empty queue when no episodes have been saved") {
            val persistence = persistenceProvider()
            persistence.get() shouldBe Queue(emptyList())
        }
    }

    describe("save") {
        it("persists episodes in insertion order") {
            val persistence = persistenceProvider()
            val ep1 = EpisodeId("ep-1")
            val ep2 = EpisodeId("ep-2")
            val ep3 = EpisodeId("ep-3")

            persistence.save(Queue(listOf(ep1, ep2, ep3)))

            persistence.get() shouldBe Queue(listOf(ep1, ep2, ep3))
        }

        it("replaces the existing queue on a second save") {
            val persistence = persistenceProvider()
            persistence.save(Queue(listOf(EpisodeId("ep-1"), EpisodeId("ep-2"))))
            persistence.save(Queue(listOf(EpisodeId("ep-3"))))

            persistence.get() shouldBe Queue(listOf(EpisodeId("ep-3")))
        }

        it("clears the queue when saving an empty queue") {
            val persistence = persistenceProvider()
            persistence.save(Queue(listOf(EpisodeId("ep-1"))))
            persistence.save(Queue(emptyList()))

            persistence.get() shouldBe Queue(emptyList())
        }
    }
}
