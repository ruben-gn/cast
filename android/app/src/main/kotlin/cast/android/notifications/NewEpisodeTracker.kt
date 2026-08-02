package cast.android.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cast.api.EpisodeDetailDto
import cast.api.PodcastSummaryDto
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Decides which episodes are "new" by comparing publishedAt against a persisted watermark.
 * [newEpisodes] is read-only; callers post notifications first and then [advance] the watermark,
 * so a crash in between re-posts (notification ids are stable) rather than silently drops.
 *
 * Both sides consider only podcasts being listened to. If [advance] took the wider set, a podcast
 * we ignore could drag the watermark past an episode of one we don't — feeds routinely surface
 * episodes whose publishedAt is hours old — and that episode would never be notified.
 */
@Singleton
class NewEpisodeTracker @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    /**
     * Episodes from listening podcasts published after the watermark, oldest first.
     * Before the first [advance] there is no watermark and nothing is new — this swallows
     * the backlog a fresh install would otherwise notify about.
     */
    suspend fun newEpisodes(
        podcasts: List<PodcastSummaryDto>,
        episodes: List<EpisodeDetailDto>,
    ): List<EpisodeDetailDto> {
        val watermark = watermark() ?: return emptyList()
        return episodes.fromListening(podcasts)
            .mapNotNull { episode -> episode.publishedAtInstant()?.let { episode to it } }
            .filter { (_, publishedAt) -> publishedAt > watermark }
            .sortedBy { (_, publishedAt) -> publishedAt }
            .map { (episode, _) -> episode }
    }

    /** Moves the watermark up to the newest publishedAt seen. Never moves it backwards. */
    suspend fun advance(podcasts: List<PodcastSummaryDto>, episodes: List<EpisodeDetailDto>) {
        val newest = episodes.fromListening(podcasts)
            .mapNotNull { it.publishedAtInstant() }
            .maxOrNull() ?: return
        dataStore.edit { prefs ->
            val current = prefs[WATERMARK]?.toInstantOrNull()
            if (current == null || newest > current) prefs[WATERMARK] = newest.toString()
        }
    }

    private fun List<EpisodeDetailDto>.fromListening(
        podcasts: List<PodcastSummaryDto>,
    ): List<EpisodeDetailDto> {
        val listening = podcasts.filter { it.listening }.map { it.id }.toSet()
        return filter { it.podcastId in listening }
    }

    private suspend fun watermark(): Instant? = dataStore.data.first()[WATERMARK]?.toInstantOrNull()

    private fun EpisodeDetailDto.publishedAtInstant(): Instant? = publishedAt?.toInstantOrNull()

    private fun String.toInstantOrNull(): Instant? = runCatching { Instant.parse(this) }.getOrNull()

    companion object {
        private val WATERMARK = stringPreferencesKey("new_episode_watermark")
    }
}
