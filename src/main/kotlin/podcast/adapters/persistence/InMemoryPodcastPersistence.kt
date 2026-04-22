package podcast.adapters.persistence

import podcast.core.model.Podcast
import podcast.core.model.PodcastSummary
import podcast.core.port.PodcastPersistence

class InMemoryPodcastPersistence : PodcastPersistence {

    private val storage = mutableMapOf<String, Podcast>()

    override suspend fun save(podcast: Podcast) { storage[podcast.id] = podcast }

    override suspend fun findAllSummaries() =
        storage.values.map { PodcastSummary(it.id, it.url, it.name, it.image, it.createdAt) }

    override suspend fun findAll() = storage.values.toList()

    override suspend fun findById(id: String) = storage[id]

    override suspend fun findByUrl(url: String) = storage.values.find { it.url == url }
}
