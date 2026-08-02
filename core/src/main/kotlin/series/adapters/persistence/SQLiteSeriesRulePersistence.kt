package series.adapters.persistence

import configuration.ConnectionProvider
import podcast.core.models.PodcastId
import series.core.models.SeriesRule
import series.core.ports.SeriesRulePersistence

class SQLiteSeriesRulePersistence(private val db: ConnectionProvider) : SeriesRulePersistence {

    override suspend fun add(rule: SeriesRule) = db.withConnection { conn ->
        conn.prepareStatement("""
            INSERT INTO series_rules (podcast_id, name) VALUES (?, ?)
            ON CONFLICT(podcast_id, name) DO NOTHING
        """.trimIndent()).use { stmt ->
            stmt.setString(1, rule.podcastId.value)
            stmt.setString(2, rule.name)
            stmt.executeUpdate()
        }
        Unit
    }

    override suspend fun remove(rule: SeriesRule): Boolean = db.withConnection { conn ->
        conn.prepareStatement("DELETE FROM series_rules WHERE podcast_id = ? AND name = ?").use { stmt ->
            stmt.setString(1, rule.podcastId.value)
            stmt.setString(2, rule.name)
            stmt.executeUpdate() > 0
        }
    }

    override suspend fun findAll(): List<SeriesRule> = db.withConnection { conn ->
        conn.prepareStatement("SELECT podcast_id, name FROM series_rules").use { stmt ->
            val rs = stmt.executeQuery()
            buildList {
                while (rs.next()) {
                    add(SeriesRule(PodcastId(rs.getString("podcast_id")), rs.getString("name")))
                }
            }
        }
    }
}
