package podcast.core.port

import podcast.core.model.Episode
import podcast.core.model.PodcastId

interface EpisodePersistence {
    suspend fun saveAll(episodes: List<Episode>)
    suspend fun findByPodcastId(podcastId: PodcastId): List<Episode>
}
