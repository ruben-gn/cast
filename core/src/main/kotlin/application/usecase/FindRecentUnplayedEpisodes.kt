package application.usecase

import playback.core.ports.PlaybackPersistence
import podcast.core.models.Episode
import podcast.core.ports.PodcastCatalog
import java.time.Clock
import java.time.temporal.ChronoUnit

class FindRecentUnplayedEpisodes(
    private val clock: Clock,
    private val catalog: PodcastCatalog,
    private val playbackPersistence: PlaybackPersistence
) {
    suspend operator fun invoke(): List<Episode> {
        val twoWeeksAgo = clock.instant().minus(14, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
        val episodes = catalog.findEpisodesPublishedAfter(twoWeeksAgo)
        val playback = playbackPersistence.getAll(episodes.map { it.id })

        return episodes
            .filter { episode -> playback[episode.id]?.played != true }
            .sortedByDescending { it.publishedAt }
    }
}