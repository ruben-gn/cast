package podcast.core

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

    suspend operator fun invoke(podcast: Podcast): Podcast {
        val feedInfo = feedInfoProvider.fetch(podcast.url)

        val updatedPodcast = podcast.copy(
            name = feedInfo.title,
            image = feedInfo.image,
            updated = clock.instant(),
        )

        val existingEpisodeIds = podcast.fetchExistingEpisodes()
        val episodes = feedInfo.episodes.filterNot { it.id in existingEpisodeIds }

        catalog.save(updatedPodcast, episodes.map { it.toEpisode(podcast.id) })

        return updatedPodcast
    }

    private suspend fun Podcast.fetchExistingEpisodes(): Set<String> =
        catalog
            .episodesFor(id)
            .map(Episode::id)
            .map(EpisodeId::value)
            .toSet()
}