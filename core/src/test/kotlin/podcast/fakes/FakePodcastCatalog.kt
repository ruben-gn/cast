package podcast.fakes

import podcast.core.models.Episode
import shared.model.EpisodeId
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog

class FakePodcastCatalog : PodcastCatalog {
    private val podcasts = mutableMapOf<PodcastId, Podcast>()
    private val episodes = mutableMapOf<EpisodeId, Episode>()

    override suspend fun save(podcast: Podcast, episodes: List<Episode>) {
        podcasts[podcast.id] = podcast
        episodes.forEach { this.episodes[it.id] = it }
    }

    override suspend fun findAll() = podcasts.values.toList()

    override suspend fun findById(id: PodcastId) = podcasts[id]

    override suspend fun findByUrl(url: FeedUrl) = podcasts.values.find { it.url == url }

    override suspend fun episodesFor(podcastId: PodcastId) =
        episodes.values.filter { it.podcastId == podcastId }

    override suspend fun findEpisodeById(id: EpisodeId) = episodes[id]
}
