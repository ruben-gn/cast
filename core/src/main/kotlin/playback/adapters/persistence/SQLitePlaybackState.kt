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

    override suspend fun markPlayed(episodeId: EpisodeId) {
        db.withConnection { conn ->
            val sql = """
                INSERT INTO playback_state (episode_id, progress_ms, updated_at, played)
                VALUES (?, 0, datetime('now'), 1)
                ON CONFLICT(episode_id) DO UPDATE SET played = 1, updated_at = datetime('now')
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, episodeId.value)
                stmt.executeUpdate()
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

    private fun ResultSet.toPlaybackState() = PlaybackState(
        episodeId = EpisodeId(getString("episode_id")),
        progressMs = getLong("progress_ms"),
        updatedAt = Instant.parse(getString("updated_at")),
        played = getBoolean("played"),
    )
}