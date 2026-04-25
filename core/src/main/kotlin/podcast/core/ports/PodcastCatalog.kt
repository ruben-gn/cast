package podcast.core.ports

import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId

interface PodcastCatalog {
    suspend fun save(podcast: Podcast, episodes: List<Episode>)
    suspend fun findAll(): List<Podcast>
    suspend fun findById(id: PodcastId): Podcast?
    suspend fun findByUrl(url: FeedUrl): Podcast?
    suspend fun episodesFor(podcastId: PodcastId): List<Episode>
}
