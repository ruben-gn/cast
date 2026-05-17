package podcast.core.usecase

import podcast.core.models.Episode
import podcast.core.models.Podcast
import podcast.core.ports.FeedInfoProvider
import podcast.core.ports.PodcastCatalog
import podcast.core.ports.toEpisode
import java.time.Clock

class UpdateFeed(
    private val catalog: PodcastCatalog,
    private val feedInfoProvider: FeedInfoProvider,
    private val clock: Clock
) {

    suspend operator fun invoke(podcast: Podcast): Result<Pair<Podcast, List<Episode>>> {
        val feedInfo = try {
            feedInfoProvider.fetch(podcast.url)
        } catch (e: Exception) {
            return Result.failure(e)
        }

        val updatedPodcast = podcast.copy(
            name = feedInfo.title,
            image = feedInfo.image,
            updated = clock.instant(),
        )

        val existingEpisodes = catalog.episodesFor(podcast.id)
        val existingGuids = existingEpisodes.map { it.feedGuid }.toSet()
        val newEpisodes = feedInfo.episodes
            .filterNot { it.guid in existingGuids }
            .map { it.toEpisode(podcast.id) }

        catalog.save(updatedPodcast, newEpisodes)

        return Result.success(updatedPodcast to (existingEpisodes + newEpisodes))
    }
}