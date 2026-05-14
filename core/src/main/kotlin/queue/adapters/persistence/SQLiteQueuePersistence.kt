package queue.adapters.persistence

import configuration.ConnectionProvider
import queue.core.model.Queue
import queue.core.ports.QueuePersistence
import shared.model.EpisodeId

class SQLiteQueuePersistence(private val db: ConnectionProvider) : QueuePersistence {

    override suspend fun get(): Queue = db.withConnection { conn ->
        conn.prepareStatement("SELECT episode_id FROM queue ORDER BY position").use { stmt ->
            val rs = stmt.executeQuery()
            val ids = buildList {
                while (rs.next()) add(EpisodeId(rs.getString("episode_id")))
            }
            Queue(ids)
        }
    }

    override suspend fun save(queue: Queue) = db.withConnection { conn ->
        val originalAutoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            conn.createStatement().use { it.executeUpdate("DELETE FROM queue") }
            if (queue.episodeIds.isNotEmpty()) {
                conn.prepareStatement("INSERT INTO queue (position, episode_id) VALUES (?, ?)").use { stmt ->
                    queue.episodeIds.forEachIndexed { index, episodeId ->
                        stmt.setInt(1, index)
                        stmt.setString(2, episodeId.value)
                        stmt.addBatch()
                    }
                    stmt.executeBatch()
                }
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = originalAutoCommit
        }
    }
}
