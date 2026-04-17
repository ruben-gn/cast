package podcast.adapters.persistence

import podcast.core.model.Episode
import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.Instant

class SQLitePodcastPersistence(private val connection: Connection) : PodcastPersistence {

    override fun save(podcast: Podcast) {
        val originalAutoCommit = connection.autoCommit
        try {
            connection.autoCommit = false

            connection.savePodcast(podcast)
            connection.saveEpisodes(podcast)

            connection.commit()
        } catch (e: Exception) {
            connection.rollback()
            throw e
        } finally {
            connection.autoCommit = originalAutoCommit
        }
    }

    override fun findAll(): List<Podcast> =
        connection.prepareStatement("SELECT * FROM podcasts").use { statement ->
            val result = statement.executeQuery()
            val podcasts = mutableListOf<Podcast>()
            while (result.next()) {
                podcasts.add(mapRowToPodcast(result))
            }
            podcasts
        }

    override fun findById(id: String): Podcast? {
        return connection.prepareStatement("SELECT * FROM podcasts WHERE id = ?").use { statement ->
            statement.setString(1, id)
            val result = statement.executeQuery()
            if (result.next()) mapRowToPodcast(result) else null
        }
    }

    override fun findByUrl(url: String): Podcast? {
        return connection.prepareStatement("SELECT * FROM podcasts WHERE url = ?").use { statement ->
            statement.setString(1, url)
            val result = statement.executeQuery()
            if (result.next()) mapRowToPodcast(result) else null
        }
    }

    private fun mapRowToPodcast(resultSet: ResultSet) =
        Podcast(
            id = resultSet.getString("id"),
            url = resultSet.getString("url"),
            name = resultSet.getString("name"),
            image = resultSet.getString("image"),
            createdAt = Instant.parse(resultSet.getString("created_at")),
            episodes = fetchEpisodesForPodcast(resultSet.getString("id"))
        )


    private fun fetchEpisodesForPodcast(podcastId: String): List<Episode> {
        return connection.prepareStatement("SELECT * FROM episodes WHERE podcast_id = ?").use { statement ->
            statement.setString(1, podcastId)
            val rs = statement.executeQuery()
            val episodes = mutableListOf<Episode>()
            while (rs.next()) {
                episodes.add(rs.toEpisode())
            }
            episodes
        }
    }

    private fun ResultSet.toEpisode(): Episode = Episode(
        id = getString("id"),
        title = getString("title"),
        description = getString("description"),
        audioUrl = getString("audio_url"),
        duration = getString("duration"),
        publishedAt = getString("published_at")?.let { Instant.parse(it) }
    )
}

private fun Connection.saveEpisodes(podcast: Podcast) =
    prepareStatement(INSERT_EPISODE_STATEMENT)
        .use { statement -> statement.saveEpisodes(podcast.id, podcast.episodes) }

private fun PreparedStatement.saveEpisodes(podcastId: String, episodes: List<Episode>) =
    episodes
        .forEach { episode -> addEpisode(episode, podcastId) }
        .also { executeBatch() }

private fun PreparedStatement.addEpisode(episode: Episode, podcastId: String) {
    setString(1, episode.id)
    setString(2, podcastId)
    setString(3, episode.title)
    setString(4, episode.description)
    setString(5, episode.audioUrl)
    setString(6, episode.duration)
    setString(7, episode.publishedAt?.toString())
    addBatch()
}

private fun Connection.savePodcast(podcast: Podcast) {
    prepareStatement(INSERT_PODCAST_STATEMENT)
        .use { statement ->
            statement.setString(1, podcast.id)
            statement.setString(2, podcast.url)
            statement.setString(3, podcast.name)
            statement.setString(4, podcast.image)
            statement.setString(5, podcast.createdAt.toString())
            statement.executeUpdate()
        }
}

private val INSERT_PODCAST_STATEMENT = """
                    INSERT INTO podcasts (id, url, name, image, created_at) 
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(id) DO UPDATE SET
                        url = excluded.url,
                        name = excluded.name,
                        image = excluded.image,
                        created_at = excluded.created_at
                    """.trimIndent()

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