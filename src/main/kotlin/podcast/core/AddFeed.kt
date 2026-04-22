package podcast.core

import io.github.oshai.kotlinlogging.KotlinLogging
import podcast.core.model.Episode
import podcast.core.model.EpisodeId
import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId
import podcast.core.port.EpisodeInfo
import podcast.core.port.EpisodePersistence
import podcast.core.port.FeedInfoProvider
import podcast.core.port.PodcastPersistence
import java.time.Clock
import java.util.*

private val log = KotlinLogging.logger {}

class AddFeed(
    private val podcasts: PodcastPersistence,
    private val episodes: EpisodePersistence,
    private val feedInfoProvider: FeedInfoProvider,
    private val clock: Clock
) {
    suspend operator fun invoke(url: FeedUrl): Podcast {
        log.info { "Adding feed $url." }

        podcasts.findByUrl(url)?.let {
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

        podcasts.save(podcast)

        val episodeList = feedInfo.episodes.map { episode(it, podcast.id) }
        episodes.saveAll(episodeList)

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
