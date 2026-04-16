package podcast.core

import io.github.oshai.kotlinlogging.KotlinLogging
import podcast.core.model.Episode
import podcast.core.model.Podcast
import podcast.core.port.EpisodeInfo
import podcast.core.port.FeedInfoProvider
import podcast.core.port.PodcastPersistence
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private val log = KotlinLogging.logger {}

class AddFeed(
    private val podcasts: PodcastPersistence,
    private val feedInfoProvider: FeedInfoProvider,
    private val clock: Clock
) {
    suspend operator fun invoke(url: String): Podcast {
        log.info { "Adding feed $url." }

        podcasts.findByUrl(url)?.let {
            log.info { "Feed $url already exists [${it.name}, ${it.id}]." }
            return it
        }

        val feedInfo = feedInfoProvider.fetch(url)

        val episodes = feedInfo.episodes.map(::episode)

        val podcast = Podcast(
            id = UUID.randomUUID().toString(),
            url = url,
            name = feedInfo.title,
            image = feedInfo.image,
            createdAt = clock.instant(),
            episodes = episodes
        )

        podcasts.save(podcast)

        log.info { "Added  feed $url: ${podcast.name} (${episodes.size} episodes)." }
        return podcast
    }
}

private fun episode(episode: EpisodeInfo) = Episode(
    id = UUID.randomUUID().toString(),
    title = episode.title,
    description = episode.description,
    audioUrl = episode.audioUrl,
    duration = episode.duration,
    publishedAt = episode.publishedAt
)