package podcast.adapters.persistence

import configuration.ConnectionProvider
import podcast.core.model.Episode
import podcast.core.model.EpisodeId
import podcast.core.model.PodcastId
import podcast.core.port.EpisodePersistence
import java.sql.ResultSet
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class SQLiteEpisodePersistence(private val db: ConnectionProvider) : EpisodePersistence {

    override suspend fun findByPodcastId(podcastId: PodcastId): List<Episode> = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM episodes WHERE podcast_id = ?").use { statement ->
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
