package queue.core

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import queue.core.model.Queue
import queue.core.ports.QueuePersistence
import queue.core.usecase.*
import queue.fakes.FakeQueuePersistence
import shared.model.EpisodeId

class QueueCoreTests : DescribeSpec({
    describe("Queue Domain Hexagon") {
        lateinit var queuePersistence: QueuePersistence

        lateinit var getQueue: GetQueue
        lateinit var addEpisodeFirst: AddEpisodeFirst
        lateinit var addEpisodeLast: AddEpisodeLast
        lateinit var addEpisodeAt: AddEpisodeAt
        lateinit var dequeueEpisode: DequeueEpisode

        beforeEach {
            queuePersistence = FakeQueuePersistence()
            getQueue = GetQueue(queuePersistence)
            addEpisodeFirst = AddEpisodeFirst(queuePersistence)
            addEpisodeLast = AddEpisodeLast(queuePersistence)
            addEpisodeAt = AddEpisodeAt(queuePersistence)
            dequeueEpisode = DequeueEpisode(queuePersistence)
        }

        describe("AddFirst") {
            it("should prepend an episode to an empty queue") {
                addEpisodeFirst(EpisodeId("ep-1"))
                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-1")))
            }

            it("should prepend an episode to a non-empty queue") {
                addEpisodeFirst(EpisodeId("ep-1"))
                addEpisodeFirst(EpisodeId("ep-2"))
                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-2"), EpisodeId("ep-1")))
            }
        }

        describe("AddLast") {
            it("should append an episode to an empty queue") {
                addEpisodeLast(EpisodeId("ep-1"))
                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-1")))
            }

            it("should append an episode to a non-empty queue") {
                addEpisodeLast(EpisodeId("ep-1"))
                addEpisodeLast(EpisodeId("ep-2"))
                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-1"), EpisodeId("ep-2")))
            }
        }

        describe("AddAt") {
            it("should insert an episode at a specific position") {
                addEpisodeFirst(EpisodeId("ep-1"))
                addEpisodeFirst(EpisodeId("ep-2"))
                addEpisodeAt(EpisodeId("ep-3"), 1)
                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-2"), EpisodeId("ep-3"), EpisodeId("ep-1")))
            }

            it("should insert an episode in an empty queue") {
                addEpisodeAt(EpisodeId("ep-1"), 0)
                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-1")))
            }


            it("should prepend an episode for position 0") {
                addEpisodeFirst(EpisodeId("ep-1"))
                addEpisodeAt(EpisodeId("ep-2"), 0)
                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-2"), EpisodeId("ep-1")))
            }

            it("should append an episode for a position greater than the queue size") {
                addEpisodeFirst(EpisodeId("ep-1"))
                addEpisodeAt(EpisodeId("ep-2"), 999)
                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-1"), EpisodeId("ep-2")))
            }

            it("should move an episode to a different position") {
                addEpisodeLast(EpisodeId("ep-1"))
                addEpisodeLast(EpisodeId("ep-2"))
                addEpisodeLast(EpisodeId("ep-3"))

                addEpisodeAt(EpisodeId("ep-3"), 1)

                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-1"), EpisodeId("ep-3"), EpisodeId("ep-2")))
            }
        }

        describe("Dequeue") {
            it("should dequeue an episode when there are no episodes remaining") {
                addEpisodeFirst(EpisodeId("ep-1"))
                dequeueEpisode(EpisodeId("ep-1"))
                getQueue() shouldBe Queue(episodeIds = listOf())
            }

            it("should dequeue an episode when there are episodes remaining") {
                addEpisodeFirst(EpisodeId("ep-1"))
                addEpisodeFirst(EpisodeId("ep-2"))
                dequeueEpisode(EpisodeId("ep-1"))
                getQueue() shouldBe Queue(episodeIds = listOf(EpisodeId("ep-2")))
            }

            it("should not fail when dequeueing an episode that is not in the queue") {
                addEpisodeFirst(EpisodeId("ep-1"))
                dequeueEpisode(EpisodeId("ep-2")) shouldBe Queue(episodeIds = listOf(EpisodeId("ep-1")))
            }
        }
    }
})