package podcast.core

import podcast.core.models.Episode
import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog

class ListEpisodes(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(podcastId: PodcastId): List<Episode> = catalog.episodesFor(podcastId)
}
