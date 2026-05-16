package application.usecase

import playback.core.usecase.GetPlaybackStates
import podcast.core.models.Episode
import podcast.core.usecase.FindRecentEpisodes
import java.time.Clock
import java.time.temporal.ChronoUnit

class FindRecentUnplayedEpisodes(
    private val clock: Clock,
    private val findRecentEpisodes: FindRecentEpisodes,
    private val getPlaybackStates: GetPlaybackStates,
) {
    suspend operator fun invoke(): List<Episode> {
        val twoWeeksAgo = clock.instant().minus(14, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
        val episodes = findRecentEpisodes(twoWeeksAgo)
        val playback = getPlaybackStates(episodes.map { it.id })

        return episodes
            .filter { episode -> playback[episode.id]?.played != true }
            .sortedByDescending { it.publishedAt }
    }
}