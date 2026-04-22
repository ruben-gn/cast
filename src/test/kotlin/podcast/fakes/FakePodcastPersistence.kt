package podcast.fakes

import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId
import podcast.core.port.PodcastPersistence

class FakePodcastPersistence : PodcastPersistence {
    private val storage = mutableMapOf<PodcastId, Podcast>()

    fun save(podcast: Podcast) { storage[podcast.id] = podcast }

    override suspend fun findAll() = storage.values.toList()

    override suspend fun findById(id: PodcastId) = storage[id]

    override suspend fun findByUrl(url: FeedUrl) = storage.values.find { it.url == url }
}
