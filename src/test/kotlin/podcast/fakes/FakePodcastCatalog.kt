package podcast.fakes

import podcast.core.model.Episode
import podcast.core.model.Podcast
import podcast.core.port.PodcastCatalog

class FakePodcastCatalog(
    private val podcasts: FakePodcastPersistence,
    private val episodes: FakeEpisodePersistence
) : PodcastCatalog {
    override suspend fun register(podcast: Podcast, episodes: List<Episode>) {
        podcasts.save(podcast)
        this.episodes.saveAll(episodes)
    }
}
