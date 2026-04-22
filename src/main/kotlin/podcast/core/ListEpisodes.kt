package podcast.core

import podcast.core.model.Episode
import podcast.core.port.EpisodePersistence

class ListEpisodes(private val episodes: EpisodePersistence) {
    suspend operator fun invoke(podcastId: String): List<Episode> = episodes.findByPodcastId(podcastId)
}
