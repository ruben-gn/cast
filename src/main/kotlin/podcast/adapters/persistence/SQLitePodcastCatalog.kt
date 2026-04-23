package podcast.adapters.persistence

import configuration.ConnectionProvider
import podcast.core.model.Episode
import shared.model.EpisodeId
import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId
import podcast.core.port.PodcastCatalog
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class SQLitePodcastCatalog(private val db: ConnectionProvider) : PodcastCatalog {

    override suspend fun add(podcast: Podcast, episodes: List<Episode>) = db.withConnection { conn ->
        val originalAutoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            conn.insertPodcast(podcast)
            if (episodes.isNotEmpty()) conn.insertEpisodes(episodes)
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = originalAutoCommit
        }
    }

    override suspend fun findAll(): List<Podcast> = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM podcasts").use { stmt ->
            val rs = stmt.executeQuery()
            generateSequence { if (rs.next()) rs.toPodcast() else null }.toList()
        }
    }

    override suspend fun findById(id: PodcastId): Podcast? = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM podcasts WHERE id = ?").use { stmt ->
            stmt.setString(1, id.value)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.toPodcast() else null
        }
    }

    override suspend fun findByUrl(url: FeedUrl): Podcast? = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM podcasts WHERE url = ?").use { stmt ->
            stmt.setString(1, url.value)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.toPodcast() else null
        }
    }

    override suspend fun episodesFor(podcastId: PodcastId): List<Episode> = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM episodes WHERE podcast_id = ?").use { stmt ->
            stmt.setString(1, podcastId.value)
            val rs = stmt.executeQuery()
            generateSequence { if (rs.next()) rs.toEpisode() else null }.toList()
        }
    }
}

private fun Connection.insertPodcast(podcast: Podcast) {
    prepareStatement(INSERT_PODCAST).use { stmt ->
        stmt.setString(1, podcast.id.value)
        stmt.setString(2, podcast.url.value)
        stmt.setString(3, podcast.name)
        stmt.setString(4, podcast.image)
        stmt.setString(5, podcast.createdAt.toString())
        stmt.executeUpdate()
    }
}

private fun Connection.insertEpisodes(episodes: List<Episode>) {
    prepareStatement(INSERT_EPISODE).use { stmt ->
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

private fun ResultSet.toPodcast() = Podcast(
    id = PodcastId(getString("id")),
    url = FeedUrl(getString("url")),
    name = getString("name"),
    image = getString("image"),
    createdAt = Instant.parse(getString("created_at"))
)

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
