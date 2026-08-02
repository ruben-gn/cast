package cast.android.ui.viewmodel

import cast.android.network.episode
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentRowsTest {

    @Test
    fun `episodes without a series name pass through in order`() {
        val a = episode("a").copy(podcastId = "p1")
        val b = episode("b").copy(podcastId = "p1")

        val rows = groupIntoRows(listOf(a, b))

        assertEquals(listOf(RecentRow.Single(a), RecentRow.Single(b)), rows)
    }

    @Test
    fun `episodes sharing a series collapse into one series row at the first member's position`() {
        val a = episode("a").copy(podcastId = "p1", seriesName = "The Divided Dial")
        val b = episode("b").copy(podcastId = "p1", seriesName = "The Divided Dial")
        val c = episode("c").copy(podcastId = "p1")

        val rows = groupIntoRows(listOf(a, b, c))

        assertEquals(
            listOf(RecentRow.Series("p1|The Divided Dial", "The Divided Dial", listOf(a, b)), RecentRow.Single(c)),
            rows,
        )
    }

    @Test
    fun `a series with a single member renders as a plain episode row`() {
        val a = episode("a").copy(podcastId = "p1", seriesName = "The Divided Dial")

        val rows = groupIntoRows(listOf(a))

        assertEquals(listOf(RecentRow.Single(a)), rows)
    }

    @Test
    fun `the same series name on two different podcasts forms two separate rows`() {
        val a1 = episode("a1").copy(podcastId = "p1", seriesName = "Same Series")
        val a2 = episode("a2").copy(podcastId = "p1", seriesName = "Same Series")
        val b1 = episode("b1").copy(podcastId = "p2", seriesName = "Same Series")
        val b2 = episode("b2").copy(podcastId = "p2", seriesName = "Same Series")

        val rows = groupIntoRows(listOf(a1, a2, b1, b2))

        assertEquals(
            listOf(
                RecentRow.Series("p1|Same Series", "Same Series", listOf(a1, a2)),
                RecentRow.Series("p2|Same Series", "Same Series", listOf(b1, b2)),
            ),
            rows,
        )
    }

    @Test
    fun `guessSeriesName returns the shared prefix stripped of trailing separators and numbers`() {
        val result = guessSeriesName(
            title = "The Divided Dial: Part 3",
            siblingTitles = listOf("The Divided Dial: Part 1"),
        )

        assertEquals("The Divided Dial: Part", result)
    }

    @Test
    fun `guessSeriesName falls back to the full title when no sibling shares a meaningful prefix`() {
        val result = guessSeriesName(
            title = "Random Episode",
            siblingTitles = listOf("Totally Different Title"),
        )

        assertEquals("Random Episode", result)
    }

    @Test
    fun `guessSeriesName falls back to the full title when there are no siblings`() {
        val result = guessSeriesName(title = "Random Episode", siblingTitles = emptyList())

        assertEquals("Random Episode", result)
    }
}
