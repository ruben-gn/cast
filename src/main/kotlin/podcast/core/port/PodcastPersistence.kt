package podcast.core.port

import podcast.core.model.Podcast

interface PodcastPersistence {
    suspend fun save(podcast: Podcast)
    suspend fun findAll(): List<Podcast>
    suspend fun findById(id: String): Podcast?
    suspend fun findByUrl(url: String): Podcast?
}
