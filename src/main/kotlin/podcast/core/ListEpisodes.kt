package podcast.core

import podcast.core.model.Episode
import podcast.core.model.PodcastId
import podcast.core.port.PodcastCatalog

class ListEpisodes(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(podcastId: PodcastId): List<Episode> = catalog.episodesFor(podcastId)
}
