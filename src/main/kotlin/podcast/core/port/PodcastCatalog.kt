package podcast.core.port

import podcast.core.model.Episode
import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId

interface PodcastCatalog {
    suspend fun add(podcast: Podcast, episodes: List<Episode>)
    suspend fun findAll(): List<Podcast>
    suspend fun findById(id: PodcastId): Podcast?
    suspend fun findByUrl(url: FeedUrl): Podcast?
    suspend fun episodesFor(podcastId: PodcastId): List<Episode>
}
