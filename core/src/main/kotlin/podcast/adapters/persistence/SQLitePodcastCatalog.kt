package podcast.adapters.persistence

import configuration.ConnectionProvider
import podcast.core.models.Episode
import shared.model.EpisodeId
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class SQLitePodcastCatalog(private val db: ConnectionProvider) : PodcastCatalog {

    override suspend fun save(podcast: Podcast, episodes: List<Episode>) = db.withConnection { conn ->
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

    override suspend fun delete(id: PodcastId) = db.withConnection { conn ->
        val originalAutoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            conn.prepareStatement("DELETE FROM episodes WHERE podcast_id = ?").use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM podcasts WHERE id = ?").use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = originalAutoCommit
        }
    }

    override suspend fun findAll(): List<Podcast> = db.withConnection { conn ->
        conn.prepareStatement(FIND_ALL).use { stmt ->
            val rs = stmt.executeQuery()
            generateSequence { if (rs.next()) rs.toPodcast() else null }.toList()
        }
    }

    override suspend fun findById(id: PodcastId): Podcast? = db.withConnection { conn ->
        conn.prepareStatement("$PODCAST_WITH_LATEST WHERE p.id = ?").use { stmt ->
            stmt.setString(1, id.value)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.toPodcast() else null
        }
    }

    override suspend fun findByUrl(url: FeedUrl): Podcast? = db.withConnection { conn ->
        conn.prepareStatement("$PODCAST_WITH_LATEST WHERE p.url = ?").use { stmt ->
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

    override suspend fun findEpisodeById(id: EpisodeId): Episode? = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM episodes WHERE id = ?").use { stmt ->
            stmt.setString(1, id.value)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.toEpisode() else null
        }
    }

    override suspend fun findEpisodesPublishedAfter(publishedAfter: Instant): List<Episode> =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT * FROM episodes WHERE published_at > ?").use { stmt ->
                stmt.setString(1, publishedAfter.toString())
                stmt.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs.toEpisode() else null }.toList()
                }
            }
        }

    override suspend fun setListening(id: PodcastId, listening: Boolean): Boolean =
        db.withConnection { conn ->
            conn.prepareStatement("UPDATE podcasts SET listening = ? WHERE id = ?").use { stmt ->
                stmt.setInt(1, if (listening) 1 else 0)
                stmt.setString(2, id.value)
                stmt.executeUpdate() > 0
            }
        }
}

private fun Connection.insertPodcast(podcast: Podcast) {
    prepareStatement(INSERT_PODCAST).use { stmt ->
        stmt.setString(1, podcast.id.value)
        stmt.setString(2, podcast.url.value)
        stmt.setString(3, podcast.name)
        stmt.setString(4, podcast.image)
        stmt.setInt(5, if (podcast.listening) 1 else 0)
        stmt.setString(6, podcast.created.toString())
        stmt.setString(7, podcast.updated.toString())
        stmt.executeUpdate()
    }
}

private fun Connection.insertEpisodes(episodes: List<Episode>) {
    prepareStatement(INSERT_EPISODE).use { stmt ->
        episodes.forEach { episode ->
            stmt.setString(1, episode.id.value)
            stmt.setString(2, episode.feedGuid)
            stmt.setString(3, episode.podcastId.value)
            stmt.setString(4, episode.title)
            stmt.setString(5, episode.description)
            stmt.setString(6, episode.audioUrl)
            if (episode.duration != null)
                stmt.setLong(7, episode.duration.inWholeSeconds)
            else
                stmt.setNull(7, Types.INTEGER)
            stmt.setString(8, episode.publishedAt?.toString())
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
    listening = getInt("listening") != 0,
    created = Instant.parse(getString("created")),
    updated = Instant.parse(getString("updated")),
    latestEpisodeAt = Instant.parse(getString("latest_episode_at")),
)

private fun ResultSet.toEpisode(): Episode {
    val durationSeconds = getLong("duration")
    return Episode(
        id = EpisodeId(getString("id")),
        feedGuid = getString("guid"),
        podcastId = PodcastId(getString("podcast_id")),
        title = getString("title"),
        description = getString("description"),
        audioUrl = getString("audio_url"),
        duration = if (wasNull()) null else durationSeconds.seconds,
        publishedAt = getString("published_at")?.let { Instant.parse(it) }
    )
}

private const val PODCAST_WITH_LATEST = """
    SELECT p.*, COALESCE(MAX(e.published_at), p.created) AS latest_episode_at
    FROM podcasts p
    LEFT JOIN episodes e ON e.podcast_id = p.id
    GROUP BY p.id
"""

private const val FIND_ALL = "$PODCAST_WITH_LATEST ORDER BY p.listening DESC, p.created ASC"

private val INSERT_PODCAST = """
    INSERT INTO podcasts (id, url, name, image, listening, created, updated)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        url = excluded.url,
        name = excluded.name,
        image = excluded.image,
        created = excluded.created,
        updated = excluded.updated
""".trimIndent()

private val INSERT_EPISODE = """
    INSERT INTO episodes
    (id, guid, podcast_id, title, description, audio_url, duration, published_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(guid) DO UPDATE SET
        title = excluded.title,
        description = excluded.description,
        audio_url = excluded.audio_url,
        duration = excluded.duration,
        published_at = excluded.published_at
""".trimIndent()
