package grootnibbel.ink.podcast.infrastructure

import grootnibbel.ink.podcast.core.Podcast
import grootnibbel.ink.podcast.core.PodcastPersistence

class InMemoryPodcastPersistence : PodcastPersistence {

    private val storage = mutableMapOf<String, Podcast>()

    override fun save(podcast: Podcast) = podcast.let { storage[podcast.id] = podcast }

    override fun findAll(): List<Podcast> = storage.values.toList()
}