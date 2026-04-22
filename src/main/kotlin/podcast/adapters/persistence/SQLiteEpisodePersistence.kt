package podcast.adapters.persistence

import configuration.DatabaseContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import podcast.core.model.Episode
import podcast.core.model.EpisodeId
import podcast.core.model.PodcastId
import podcast.core.port.EpisodePersistence
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class SQLiteEpisodePersistence(private val db: DatabaseContext) : EpisodePersistence {

    override suspend fun saveAll(episodes: List<Episode>) = withContext(Dispatchers.IO) {
        if (episodes.isEmpty()) return@withContext
        db.mutex.withLock {
            val originalAutoCommit = db.connection.autoCommit
            try {
                db.connection.autoCommit = false
                db.connection.prepareStatement(INSERT_EPISODE_STATEMENT).use { statement ->
                    episodes.forEach { episode ->
                        statement.setString(1, episode.id.value)
                        statement.setString(2, episode.podcastId.value)
                        statement.setString(3, episode.title)
                        statement.setString(4, episode.description)
                        statement.setString(5, episode.audioUrl)
                        if (episode.duration != null)
                            statement.setLong(6, episode.duration.inWholeSeconds)
                        else
                            statement.setNull(6, Types.INTEGER)
                        statement.setString(7, episode.publishedAt?.toString())
                        statement.addBatch()
                    }
                    statement.executeBatch()
                }
                db.connection.commit()
            } catch (e: Exception) {
                db.connection.rollback()
                throw e
            } finally {
                db.connection.autoCommit = originalAutoCommit
            }
        }
    }

    override suspend fun findByPodcastId(podcastId: PodcastId): List<Episode> = withContext(Dispatchers.IO) {
        db.mutex.withLock {
            db.connection.prepareStatement("SELECT * FROM episodes WHERE podcast_id = ?").use { statement ->
                statement.setString(1, podcastId.value)
                val rs = statement.executeQuery()
                val episodes = mutableListOf<Episode>()
                while (rs.next()) {
                    episodes.add(rs.toEpisode())
                }
                episodes
            }
        }
    }
}

private fun ResultSet.toEpisode(): Episode {
    val durationSeconds = getLong("duration")
    return Episode(
        id = EpisodeId(getString("id")),
        podcastId = PodcastId(getString("podcast_id")),
        title = getString("title"),
        description = getString("description"),
        audioUrl = getString("audio_url"),
        duration = if (wasNull()) null else durationSeconds.seconds,
        publishedAt = getString("published_at")?.let { Instant.parse(it) }
    )
}

private val INSERT_EPISODE_STATEMENT = """
    INSERT INTO episodes
    (id, podcast_id, title, description, audio_url, duration, published_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        podcast_id = excluded.podcast_id,
        title = excluded.title,
        description = excluded.description,
        audio_url = excluded.audio_url,
        duration = excluded.duration,
        published_at = excluded.published_at
""".trimIndent()
