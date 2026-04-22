package podcast.fakes

import podcast.core.model.Episode
import podcast.core.port.EpisodePersistence

class FakeEpisodePersistence : EpisodePersistence {
    private val storage = mutableMapOf<String, Episode>()

    override suspend fun saveAll(episodes: List<Episode>) { episodes.forEach { storage[it.id] = it } }

    override suspend fun findByPodcastId(podcastId: String) =
        storage.values.filter { it.podcastId == podcastId }
}
