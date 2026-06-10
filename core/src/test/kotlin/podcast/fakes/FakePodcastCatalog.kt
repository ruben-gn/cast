package podcast.fakes

import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog
import shared.model.EpisodeId
import java.time.Instant

class FakePodcastCatalog : PodcastCatalog {
    private val podcasts = mutableMapOf<PodcastId, Podcast>()

    // Keyed by feedGuid to mirror SQLite's UNIQUE(guid) upsert, which keeps the original id
    // and never touches `listening` on the podcast row.
    private val episodes = mutableMapOf<String, Episode>()

    override suspend fun save(podcast: Podcast, episodes: List<Episode>) {
        val existing = podcasts[podcast.id]
        podcasts[podcast.id] = if (existing == null) podcast else podcast.copy(listening = existing.listening)
        episodes.forEach { episode ->
            val current = this.episodes[episode.feedGuid]
            this.episodes[episode.feedGuid] =
                if (current == null) episode
                else current.copy(
                    title = episode.title,
                    description = episode.description,
                    audioUrl = episode.audioUrl,
                    duration = episode.duration,
                    publishedAt = episode.publishedAt,
                )
        }
    }

    override suspend fun delete(id: PodcastId) {
        podcasts.remove(id)
        episodes.values.removeIf { it.podcastId == id }
    }

    override suspend fun findAll() = podcasts.values
        .sortedWith(compareByDescending<Podcast> { it.listening }.thenBy { it.created })
        .map { it.withLatestEpisode() }

    override suspend fun findById(id: PodcastId) = podcasts[id]?.withLatestEpisode()

    override suspend fun findByUrl(url: FeedUrl) = podcasts.values.find { it.url == url }?.withLatestEpisode()

    private fun Podcast.withLatestEpisode(): Podcast {
        val latest = episodes.values
            .filter { it.podcastId == id }
            .mapNotNull { it.publishedAt }
            .maxOrNull() ?: created
        return copy(latestEpisodeAt = latest)
    }

    override suspend fun episodesFor(podcastId: PodcastId) =
        episodes.values.filter { it.podcastId == podcastId }

    override suspend fun findEpisodeById(id: EpisodeId) = episodes.values.find { it.id == id }

    override suspend fun findEpisodesPublishedAfter(publishedAfter: Instant): List<Episode> =
        episodes.values.filter { it.publishedAt?.isAfter(publishedAfter) ?: false }.toList()

    override suspend fun setListening(id: PodcastId, listening: Boolean): Boolean {
        val existing = podcasts[id] ?: return false
        podcasts[id] = existing.copy(listening = listening)
        return true
    }
}
