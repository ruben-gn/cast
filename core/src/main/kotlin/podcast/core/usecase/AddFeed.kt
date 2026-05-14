package podcast.core.usecase

import io.github.oshai.kotlinlogging.KotlinLogging
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.ports.FeedInfoProvider
import podcast.core.ports.PodcastCatalog
import podcast.core.ports.toEpisode
import podcast.core.ports.toPodcast
import java.time.Clock
import java.util.*

private val log = KotlinLogging.logger {}

class AddFeed(
    private val catalog: PodcastCatalog,
    private val feedInfoProvider: FeedInfoProvider,
    private val updateFeed: UpdateFeed,
    private val clock: Clock
) {
    suspend operator fun invoke(url: FeedUrl): Podcast {
        log.info { "Adding feed $url." }

        catalog.findByUrl(url)?.let { podcast ->
            log.info { "Feed $url already exists [${podcast.name}, ${podcast.id}]." }
            return updateFeed(podcast).getOrThrow().first
        }

        val feedInfo = feedInfoProvider.fetch(url)

        val podcast = feedInfo.toPodcast(
            PodcastId(UUID.randomUUID().toString()),
            clock.instant(),
            clock.instant()
        )

        val episodeList = feedInfo.episodes.map { it.toEpisode(podcast.id) }

        catalog.save(podcast, episodeList)

        log.info { "Added feed $url: ${podcast.name} (${episodeList.size} episodes)." }
        return podcast
    }
}