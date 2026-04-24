package podcast.core

import io.github.oshai.kotlinlogging.KotlinLogging
import podcast.core.models.Episode
import shared.model.EpisodeId
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.ports.EpisodeInfo
import podcast.core.ports.FeedInfoProvider
import podcast.core.ports.PodcastCatalog
import java.time.Clock
import java.util.*

private val log = KotlinLogging.logger {}

class AddFeed(
    private val catalog: PodcastCatalog,
    private val feedInfoProvider: FeedInfoProvider,
    private val clock: Clock
) {
    suspend operator fun invoke(url: FeedUrl): Podcast {
        log.info { "Adding feed $url." }

        catalog.findByUrl(url)?.let {
            log.info { "Feed $url already exists [${it.name}, ${it.id}]." }
            return it
        }

        val feedInfo = try {
            feedInfoProvider.fetch(url)
        } catch (e: Exception) {
            throw PodcastException.FeedFetchFailed(url, e)
        }

        val podcast = Podcast(
            id = PodcastId(UUID.randomUUID().toString()),
            url = url,
            name = feedInfo.title,
            image = feedInfo.image,
            createdAt = clock.instant()
        )

        val episodeList = feedInfo.episodes.map { episode(it, podcast.id) }
        catalog.add(podcast, episodeList)

        log.info { "Added feed $url: ${podcast.name} (${episodeList.size} episodes)." }
        return podcast
    }
}

private fun episode(episode: EpisodeInfo, podcastId: PodcastId) = Episode(
    id = EpisodeId(UUID.randomUUID().toString()),
    podcastId = podcastId,
    title = episode.title,
    description = episode.description,
    audioUrl = episode.audioUrl,
    duration = episode.duration,
    publishedAt = episode.publishedAt
)
