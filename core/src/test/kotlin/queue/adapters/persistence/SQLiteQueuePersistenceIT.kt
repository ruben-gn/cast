package queue.adapters.persistence

import configuration.CREATE_QUEUE_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import queue.core.model.Queue
import shared.model.EpisodeId
import java.sql.DriverManager

class SQLiteQueuePersistenceIT : DescribeSpec({

    lateinit var db: SingleConnectionProvider
    lateinit var persistence: SQLiteQueuePersistence

    beforeEach {
        db = SingleConnectionProvider(DriverManager.getConnection("jdbc:sqlite::memory:"))
        persistence = SQLiteQueuePersistence(db)
        db.withConnection { conn ->
            conn.createStatement().use { it.execute(CREATE_QUEUE_TABLE) }
        }
    }

    afterEach { db.close() }

    describe("get") {
        it("returns an empty queue when no episodes have been saved") {
            persistence.get() shouldBe Queue(emptyList())
        }
    }

    describe("save") {
        it("persists episodes in insertion order") {
            val ep1 = EpisodeId("ep-1")
            val ep2 = EpisodeId("ep-2")
            val ep3 = EpisodeId("ep-3")

            persistence.save(Queue(listOf(ep1, ep2, ep3)))

            persistence.get() shouldBe Queue(listOf(ep1, ep2, ep3))
        }

        it("replaces the existing queue on a second save") {
            persistence.save(Queue(listOf(EpisodeId("ep-1"), EpisodeId("ep-2"))))
            persistence.save(Queue(listOf(EpisodeId("ep-3"))))

            persistence.get() shouldBe Queue(listOf(EpisodeId("ep-3")))
        }

        it("clears the queue when saving an empty queue") {
            persistence.save(Queue(listOf(EpisodeId("ep-1"))))
            persistence.save(Queue(emptyList()))

            persistence.get() shouldBe Queue(emptyList())
        }
    }
})
