package podcast.core.port

import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId

interface PodcastPersistence {
    suspend fun findAll(): List<Podcast>
    suspend fun findById(id: PodcastId): Podcast?
    suspend fun findByUrl(url: FeedUrl): Podcast?
}
