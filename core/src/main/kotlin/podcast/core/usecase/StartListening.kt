package podcast.core.usecase

import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog

class StartListening(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(id: PodcastId): Boolean = catalog.setListening(id, true)
}
