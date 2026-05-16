package podcast.core.usecase

import podcast.core.models.Episode
import podcast.core.ports.PodcastCatalog
import shared.model.EpisodeId

class FindEpisode(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(id: EpisodeId): Episode? = catalog.findEpisodeById(id)
}
