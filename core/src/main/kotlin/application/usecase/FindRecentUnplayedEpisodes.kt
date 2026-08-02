package application.usecase

import application.model.EpisodeInContext
import playback.core.usecase.GetPlaybackStates
import podcast.core.usecase.FindRecentEpisodes
import podcast.core.usecase.ListPodcasts
import series.core.usecase.ListSeriesRules
import settings.core.usecase.GetSettings
import java.time.Clock
import java.time.temporal.ChronoUnit

class FindRecentUnplayedEpisodes(
    private val clock: Clock,
    private val findRecentEpisodes: FindRecentEpisodes,
    private val getPlaybackStates: GetPlaybackStates,
    private val listPodcasts: ListPodcasts,
    private val getSettings: GetSettings,
    private val listSeriesRules: ListSeriesRules,
) {
    suspend operator fun invoke(): List<EpisodeInContext> {
        val twoWeeksAgo = clock.instant().minus(14, ChronoUnit.DAYS).truncatedTo(ChronoUnit.DAYS)
        val episodes = findRecentEpisodes(twoWeeksAgo)
        val playback = getPlaybackStates(episodes.map { it.id })
        val podcasts = listPodcasts().associateBy { it.id }
        val settings = getSettings()
        val rules = listSeriesRules()

        return episodes
            .filter { episode -> playback[episode.id]?.played != true }
            .filter { episode ->
                !settings.recentListeningOnly || podcasts[episode.podcastId]?.listening == true
            }
            .sortedByDescending { it.publishedAt }
            .mapNotNull { episode ->
                val podcast = podcasts[episode.podcastId] ?: return@mapNotNull null
                val state = playback[episode.id]
                EpisodeInContext(
                    episode = episode,
                    progressMs = state?.progressMs ?: 0,
                    played = state?.played ?: false,
                    podcastName = podcast.name,
                    podcastImage = podcast.image,
                    seriesName = rules
                        .filter { it.podcastId == episode.podcastId && episode.title.contains(it.name, ignoreCase = true) }
                        .maxByOrNull { it.name.length }
                        ?.name,
                )
            }
    }
}
