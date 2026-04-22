package podcast.fakes

import podcast.core.model.Episode
import podcast.core.model.EpisodeId
import podcast.core.model.PodcastId
import podcast.core.port.EpisodePersistence

class FakeEpisodePersistence : EpisodePersistence {
    private val storage = mutableMapOf<EpisodeId, Episode>()

    fun saveAll(episodes: List<Episode>) { episodes.forEach { storage[it.id] = it } }

    override suspend fun findByPodcastId(podcastId: PodcastId) =
        storage.values.filter { it.podcastId == podcastId }
}
