package podcast.fakes

import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence

class FakePodcastPersistence : PodcastPersistence {
    private val storage = mutableMapOf<String, Podcast>()

    override suspend fun save(podcast: Podcast) { storage[podcast.id] = podcast }

    override suspend fun findAll() = storage.values.toList()

    override suspend fun findById(id: String) = storage[id]

    override suspend fun findByUrl(url: String) = storage.values.find { it.url == url }
}
