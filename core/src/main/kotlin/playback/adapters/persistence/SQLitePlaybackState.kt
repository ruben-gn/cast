package playback.adapters.persistence

import configuration.ConnectionProvider
import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.sql.ResultSet
import java.time.Instant

class SQLitePlaybackState(private val db: ConnectionProvider) : PlaybackPersistence {

    override suspend fun update(playbackState: PlaybackState) {
        db.withConnection { conn ->
            val sql = """
                INSERT OR REPLACE INTO playback_state (episode_id, progress_ms, updated_at)
                VALUES (?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, playbackState.episodeId.value)
                stmt.setLong(2, playbackState.progressMs)
                stmt.setString(3, playbackState.updatedAt.toString())
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
        updatedAt = Instant.parse(getString("updated_at"))
    )
}