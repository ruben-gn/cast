package queue.adapters.persistence

import configuration.CREATE_QUEUE_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import queue.core.ports.queuePersistenceContract
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

    include(queuePersistenceContract { persistence })
})
