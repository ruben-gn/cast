package podcast.core

import podcast.core.model.Podcast
import podcast.core.model.PodcastId
import podcast.core.port.PodcastCatalog

class GetPodcast(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(id: PodcastId): Podcast? = catalog.findById(id)
}
