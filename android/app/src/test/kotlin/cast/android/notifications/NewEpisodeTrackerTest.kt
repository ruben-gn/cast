package cast.android.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import cast.api.EpisodeDetailDto
import cast.api.PodcastSummaryDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class NewEpisodeTrackerTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun tracker(scope: CoroutineScope): NewEpisodeTracker {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) {
            File(tmp.newFolder(), "tracker.preferences_pb")
        }
        return NewEpisodeTracker(dataStore)
    }

    private fun podcast(id: String, listening: Boolean = true) = PodcastSummaryDto(
        id = id,
        url = "http://feed/$id",
        name = id,
        image = "",
        listening = listening,
        created = "2026-01-01T00:00:00Z",
        latestEpisodeAt = "2026-01-01T00:00:00Z",
    )

    private fun episode(id: String, podcastId: String, publishedAt: String?) = EpisodeDetailDto(
        id = id,
        title = "Episode $id",
        description = "",
        audioUrl = "http://audio/$id",
        duration = null,
        durationMs = null,
        publishedAt = publishedAt,
        played = false,
        progressMs = 0,
        podcastId = podcastId,
        podcastName = podcastId,
        podcastImage = null,
    )

    @Test
    fun `first run reports nothing and advance initializes the watermark`() = runTest {
        val tracker = tracker(backgroundScope)
        val podcasts = listOf(podcast("p1"))
        val backlog = listOf(episode("e1", "p1", "2026-06-01T10:00:00Z"))

        assertEquals(emptyList<EpisodeDetailDto>(), tracker.newEpisodes(podcasts, backlog))
        tracker.advance(backlog)

        val later = episode("e2", "p1", "2026-06-02T10:00:00Z")
        assertEquals(listOf(later), tracker.newEpisodes(podcasts, backlog + later))
    }

    @Test
    fun `reports episodes published after the watermark oldest first`() = runTest {
        val tracker = tracker(backgroundScope)
        val podcasts = listOf(podcast("p1"))
        tracker.advance(listOf(episode("e0", "p1", "2026-06-01T00:00:00Z")))

        val second = episode("e2", "p1", "2026-06-03T00:00:00Z")
        val first = episode("e1", "p1", "2026-06-02T00:00:00Z")

        assertEquals(listOf(first, second), tracker.newEpisodes(podcasts, listOf(second, first)))
    }

    @Test
    fun `ignores episodes from podcasts not being listened to`() = runTest {
        val tracker = tracker(backgroundScope)
        val podcasts = listOf(podcast("heard"), podcast("unheard", listening = false))
        tracker.advance(listOf(episode("e0", "heard", "2026-06-01T00:00:00Z")))

        val episodes = listOf(
            episode("e1", "heard", "2026-06-02T00:00:00Z"),
            episode("e2", "unheard", "2026-06-02T00:00:00Z"),
        )

        assertEquals(listOf("e1"), tracker.newEpisodes(podcasts, episodes).map { it.id })
    }

    @Test
    fun `ignores episodes published before the watermark`() = runTest {
        val tracker = tracker(backgroundScope)
        val podcasts = listOf(podcast("p1"))
        tracker.advance(listOf(episode("e0", "p1", "2026-06-05T00:00:00Z")))

        // An old episode reappearing (e.g. marked unplayed again) must not look new.
        val old = episode("e1", "p1", "2026-06-01T00:00:00Z")

        assertEquals(emptyList<EpisodeDetailDto>(), tracker.newEpisodes(podcasts, listOf(old)))
    }

    @Test
    fun `advance never moves the watermark backwards`() = runTest {
        val tracker = tracker(backgroundScope)
        val podcasts = listOf(podcast("p1"))
        tracker.advance(listOf(episode("e0", "p1", "2026-06-05T00:00:00Z")))

        tracker.advance(listOf(episode("e1", "p1", "2026-06-01T00:00:00Z")))

        val between = episode("e2", "p1", "2026-06-03T00:00:00Z")
        assertEquals(emptyList<EpisodeDetailDto>(), tracker.newEpisodes(podcasts, listOf(between)))
    }

    @Test
    fun `ignores episodes without a publishedAt`() = runTest {
        val tracker = tracker(backgroundScope)
        val podcasts = listOf(podcast("p1"))
        tracker.advance(listOf(episode("e0", "p1", "2026-06-01T00:00:00Z")))

        val undated = episode("e1", "p1", null)

        assertEquals(emptyList<EpisodeDetailDto>(), tracker.newEpisodes(podcasts, listOf(undated)))
    }
}
