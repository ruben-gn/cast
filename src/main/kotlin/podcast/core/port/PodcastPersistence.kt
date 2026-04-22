package podcast.core.port

import podcast.core.model.Podcast
import podcast.core.model.PodcastSummary

interface PodcastPersistence {
    suspend fun save(podcast: Podcast)
    suspend fun findAllSummaries(): List<PodcastSummary>
    suspend fun findAll(): List<Podcast>
    suspend fun findById(id: String): Podcast?
    suspend fun findByUrl(url: String): Podcast?
}
