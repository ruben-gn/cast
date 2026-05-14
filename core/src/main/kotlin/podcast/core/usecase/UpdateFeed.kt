package podcast.core.usecase

import podcast.core.models.Episode
import podcast.core.models.Podcast
import podcast.core.ports.FeedInfoProvider
import podcast.core.ports.PodcastCatalog
import podcast.core.ports.toEpisode
import shared.model.EpisodeId
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

        val existingEpisodeIds = podcast.fetchExistingEpisodes()
        val episodes = feedInfo.episodes.map { it.toEpisode(podcast.id) }

        catalog.save(updatedPodcast, episodes.filterNot { episode -> episode.id in existingEpisodeIds })

        return Result.success(updatedPodcast to episodes)
    }

    private suspend fun Podcast.fetchExistingEpisodes(): Set<EpisodeId> =
        catalog
            .episodesFor(id)
            .map(Episode::id)
            .toSet()
}