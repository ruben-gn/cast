package podcast.adapters.persistence

import configuration.DatabaseContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence
import java.sql.ResultSet
import java.time.Instant

class SQLitePodcastPersistence(private val db: DatabaseContext) : PodcastPersistence {

    override suspend fun save(podcast: Podcast): Unit = withContext(Dispatchers.IO) {
        db.mutex.withLock {
            db.connection.prepareStatement(INSERT_PODCAST_STATEMENT).use { statement ->
                statement.setString(1, podcast.id)
                statement.setString(2, podcast.url)
                statement.setString(3, podcast.name)
                statement.setString(4, podcast.image)
                statement.setString(5, podcast.createdAt.toString())
                statement.executeUpdate()
            }
        }
    }

    override suspend fun findAll(): List<Podcast> = withContext(Dispatchers.IO) {
        db.mutex.withLock {
            db.connection.prepareStatement("SELECT * FROM podcasts").use { statement ->
                val result = statement.executeQuery()
                val podcasts = mutableListOf<Podcast>()
                while (result.next()) {
                    podcasts.add(result.toPodcast())
                }
                podcasts
            }
        }
    }

    override suspend fun findById(id: String): Podcast? = withContext(Dispatchers.IO) {
        db.mutex.withLock {
            db.connection.prepareStatement("SELECT * FROM podcasts WHERE id = ?").use { statement ->
                statement.setString(1, id)
                val result = statement.executeQuery()
                if (result.next()) result.toPodcast() else null
            }
        }
    }

    override suspend fun findByUrl(url: String): Podcast? = withContext(Dispatchers.IO) {
        db.mutex.withLock {
            db.connection.prepareStatement("SELECT * FROM podcasts WHERE url = ?").use { statement ->
                statement.setString(1, url)
                val result = statement.executeQuery()
                if (result.next()) result.toPodcast() else null
            }
        }
    }
}

private fun ResultSet.toPodcast() = Podcast(
    id = getString("id"),
    url = getString("url"),
    name = getString("name"),
    image = getString("image"),
    createdAt = Instant.parse(getString("created_at"))
)

private val INSERT_PODCAST_STATEMENT = """
    INSERT INTO podcasts (id, url, name, image, created_at)
    VALUES (?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        url = excluded.url,
        name = excluded.name,
        image = excluded.image,
        created_at = excluded.created_at
""".trimIndent()
