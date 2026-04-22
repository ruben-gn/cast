package podcast.fakes

import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence

class FakePersistence : PodcastPersistence {
    private val storage = mutableMapOf<String, Podcast>()

    override fun save(podcast: Podcast) {
        storage[podcast.id] = podcast
    }

    override fun findAll() = storage.values.toList()

    override fun findById(id: String) = storage[id]

    override fun findByUrl(url: String) = storage.values.find { it.url == url }
}