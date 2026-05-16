package playback.adapters.persistence

import configuration.ConnectionProvider
import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.sql.ResultSet
import java.time.Instant

class SQLitePlaybackState(private val db: ConnectionProvider) : PlaybackPersistence {

    override suspend fun updateProgress(episodeId: EpisodeId, progressMs: Long, updatedAt: Instant) {
        db.withConnection { conn ->
            val sql = """
                INSERT INTO playback_state (episode_id, progress_ms, updated_at, played)
                VALUES (?, ?, ?, 0)
                ON CONFLICT(episode_id) DO UPDATE SET
                    progress_ms = excluded.progress_ms,
                    updated_at = excluded.updated_at
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, episodeId.value)
                stmt.setLong(2, progressMs)
                stmt.setString(3, updatedAt.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun resetProgress(episodeId: EpisodeId, progressMs: Long, updatedAt: Instant) {
        db.withConnection { conn ->
            val sql = """
                INSERT INTO playback_state (episode_id, progress_ms, updated_at, played)
                VALUES (?, ?, ?, 0)
                ON CONFLICT(episode_id) DO UPDATE SET
                    progress_ms = excluded.progress_ms,
                    updated_at = excluded.updated_at,
                    played = 0
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, episodeId.value)
                stmt.setLong(2, progressMs)
                stmt.setString(3, updatedAt.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun markPlayed(episodeId: EpisodeId) {
        db.withConnection { conn ->
            val sql = """
                INSERT INTO playback_state (episode_id, progress_ms, updated_at, played)
                VALUES (?, 0, strftime('%Y-%m-%dT%H:%M:%SZ', 'now'), 1)
                ON CONFLICT(episode_id) DO UPDATE SET played = 1, updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, episodeId.value)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun markUnplayed(episodeId: EpisodeId) {
        db.withConnection { conn ->
            val sql = """
                INSERT INTO playback_state (episode_id, progress_ms, updated_at, played)
                VALUES (?, 0, strftime('%Y-%m-%dT%H:%M:%SZ', 'now'), 0)
                ON CONFLICT(episode_id) DO UPDATE SET played = 0, updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, episodeId.value)
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun markAllPlayed(episodeIds: List<EpisodeId>) {
        if (episodeIds.isEmpty()) return
        db.withConnection { conn ->
            val sql = """
                INSERT INTO playback_state (episode_id, progress_ms, updated_at, played)
                VALUES (?, 0, strftime('%Y-%m-%dT%H:%M:%SZ', 'now'), 1)
                ON CONFLICT(episode_id) DO UPDATE SET played = 1, updated_at = strftime('%Y-%m-%dT%H:%M:%SZ', 'now')
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                for (id in episodeIds) {
                    stmt.setString(1, id.value)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
        }
    }

    override suspend fun get(episodeId: EpisodeId): PlaybackState? = db.withConnection { conn ->
        val sql = "SELECT * FROM playback_state WHERE episode_id = ?"

        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, episodeId.value)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.toPlaybackState() else null
        }
    }

    override suspend fun getAll(ids: List<EpisodeId>): Map<EpisodeId, PlaybackState> {
        if (ids.isEmpty()) return emptyMap()
        return db.withConnection { conn ->
            val placeholders = ids.joinToString(",") { "?" }
            conn.prepareStatement("SELECT * FROM playback_state WHERE episode_id IN ($placeholders)").use { stmt ->
                ids.forEachIndexed { i, id -> stmt.setString(i + 1, id.value) }
                val rs = stmt.executeQuery()
                buildMap {
                    while (rs.next()) {
                        val state = rs.toPlaybackState()
                        put(state.episodeId, state)
                    }
                }
            }
        }
    }

    private fun ResultSet.toPlaybackState() = PlaybackState(
        episodeId = EpisodeId(getString("episode_id")),
        progressMs = getLong("progress_ms"),
        updatedAt = Instant.parse(getString("updated_at")),
        played = getBoolean("played"),
    )
}