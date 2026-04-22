package podcast.core.port

import podcast.core.model.Episode

interface EpisodePersistence {
    suspend fun saveAll(episodes: List<Episode>)
    suspend fun findByPodcastId(podcastId: String): List<Episode>
}
