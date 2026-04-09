package podcast.infrastructure

import podcast.core.model.Podcast
import podcast.core.port.PodcastPersistence

class InMemoryPodcastPersistence : PodcastPersistence {

    private val storage = mutableMapOf<String, Podcast>()

    override fun save(podcast: Podcast) = podcast.let { storage[podcast.id] = podcast }

    override fun findAll(): List<Podcast> = storage.values.toList()
}