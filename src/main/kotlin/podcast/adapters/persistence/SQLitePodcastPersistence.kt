package podcast.adapters.persistence

import configuration.ConnectionProvider
import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId
import podcast.core.port.PodcastPersistence
import java.sql.ResultSet
import java.time.Instant

class SQLitePodcastPersistence(private val db: ConnectionProvider) : PodcastPersistence {

    override suspend fun findAll(): List<Podcast> = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM podcasts").use { statement ->
            val result = statement.executeQuery()
            val podcasts = mutableListOf<Podcast>()
            while (result.next()) {
                podcasts.add(result.toPodcast())
            }
            podcasts
        }
    }

    override suspend fun findById(id: PodcastId): Podcast? = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM podcasts WHERE id = ?").use { statement ->
            statement.setString(1, id.value)
            val result = statement.executeQuery()
            if (result.next()) result.toPodcast() else null
        }
    }

    override suspend fun findByUrl(url: FeedUrl): Podcast? = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM podcasts WHERE url = ?").use { statement ->
            statement.setString(1, url.value)
            val result = statement.executeQuery()
            if (result.next()) result.toPodcast() else null
        }
    }
}

private fun ResultSet.toPodcast() = Podcast(
    id = PodcastId(getString("id")),
    url = FeedUrl(getString("url")),
    name = getString("name"),
    image = getString("image"),
    createdAt = Instant.parse(getString("created_at"))
)
