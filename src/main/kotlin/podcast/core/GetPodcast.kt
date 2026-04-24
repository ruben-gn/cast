package podcast.core

import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog

class GetPodcast(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(id: PodcastId): Podcast? = catalog.findById(id)
}
