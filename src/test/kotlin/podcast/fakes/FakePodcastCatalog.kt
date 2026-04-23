package podcast.fakes

import podcast.core.model.Episode
import shared.model.EpisodeId
import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId
import podcast.core.port.PodcastCatalog

class FakePodcastCatalog : PodcastCatalog {
    private val podcasts = mutableMapOf<PodcastId, Podcast>()
    private val episodes = mutableMapOf<EpisodeId, Episode>()

    override suspend fun add(podcast: Podcast, episodes: List<Episode>) {
        podcasts[podcast.id] = podcast
        episodes.forEach { this.episodes[it.id] = it }
    }

    override suspend fun findAll() = podcasts.values.toList()

    override suspend fun findById(id: PodcastId) = podcasts[id]

    override suspend fun findByUrl(url: FeedUrl) = podcasts.values.find { it.url == url }

    override suspend fun episodesFor(podcastId: PodcastId) =
        episodes.values.filter { it.podcastId == podcastId }
}
