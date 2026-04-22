package podcast.adapters.persistence

import configuration.ConnectionProvider
import podcast.core.model.Episode
import podcast.core.model.Podcast
import podcast.core.port.PodcastCatalog
import java.sql.Types

class SQLitePodcastCatalog(private val db: ConnectionProvider) : PodcastCatalog {

    override suspend fun register(podcast: Podcast, episodes: List<Episode>) = db.withConnection { conn ->
        val originalAutoCommit = conn.autoCommit
        try {
            conn.autoCommit = false

            conn.prepareStatement(INSERT_PODCAST).use { stmt ->
                stmt.setString(1, podcast.id.value)
                stmt.setString(2, podcast.url.value)
                stmt.setString(3, podcast.name)
                stmt.setString(4, podcast.image)
                stmt.setString(5, podcast.createdAt.toString())
                stmt.executeUpdate()
            }

            if (episodes.isNotEmpty()) {
                conn.prepareStatement(INSERT_EPISODE).use { stmt ->
                    episodes.forEach { episode ->
                        stmt.setString(1, episode.id.value)
                        stmt.setString(2, episode.podcastId.value)
                        stmt.setString(3, episode.title)
                        stmt.setString(4, episode.description)
                        stmt.setString(5, episode.audioUrl)
                        if (episode.duration != null)
                            stmt.setLong(6, episode.duration.inWholeSeconds)
                        else
                            stmt.setNull(6, Types.INTEGER)
                        stmt.setString(7, episode.publishedAt?.toString())
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

private val INSERT_PODCAST = """
    INSERT INTO podcasts (id, url, name, image, created_at)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        url = excluded.url,
        name = excluded.name,
        image = excluded.image,
        created_at = excluded.created_at
""".trimIndent()

private val INSERT_EPISODE = """
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
