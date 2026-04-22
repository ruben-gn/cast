package podcast.adapters.persistence

import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId
import podcast.core.port.PodcastPersistence

class InMemoryPodcastPersistence : PodcastPersistence {

    private val storage = mutableMapOf<PodcastId, Podcast>()

    override suspend fun findAll() = storage.values.toList()

    override suspend fun findById(id: PodcastId) = storage[id]

    override suspend fun findByUrl(url: FeedUrl) = storage.values.find { it.url == url }
}
