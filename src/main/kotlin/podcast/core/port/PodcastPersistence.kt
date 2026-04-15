package podcast.core.port

import podcast.core.model.Podcast

interface PodcastPersistence {
    fun save(podcast: Podcast)
    fun findAll(): List<Podcast>
    fun findById(id: String): Podcast?
    fun findByUrl(url: String): Podcast?
}