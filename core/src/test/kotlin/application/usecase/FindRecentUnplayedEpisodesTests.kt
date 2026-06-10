package application.usecase

import application.usecase.FindRecentUnplayedEpisodes
import fakes.TestClock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import playback.core.usecase.GetPlaybackStates
import playback.fakes.FakePlaybackPersistence
import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.usecase.FindRecentEpisodes
import podcast.fakes.FakePodcastCatalog
import shared.model.EpisodeId
import java.time.Instant

class FindRecentUnplayedEpisodesTests : DescribeSpec({
    val now = Instant.parse("2026-04-10T10:00:00Z")
    // use case cuts off at truncatedTo(DAYS): 2026-03-27T00:00:00Z
    val withinTwoWeeks = Instant.parse("2026-03-27T00:00:01Z")
    val olderThanTwoWeeks = Instant.parse("2026-03-26T23:59:59Z")

    lateinit var clock: TestClock
    lateinit var catalog: FakePodcastCatalog
    lateinit var playback: FakePlaybackPersistence

    fun useCase() = FindRecentUnplayedEpisodes(clock, FindRecentEpisodes(catalog), GetPlaybackStates(playback))

    fun episode(id: String, publishedAt: Instant) = Episode(
        id = EpisodeId(id),
        feedGuid = id,
        podcastId = PodcastId("pod-1"),
        title = "Episode $id",
        description = "",
        audioUrl = "https://cdn/$id.mp3",
        duration = null,
        publishedAt = publishedAt,
    )

    beforeEach {
        clock = TestClock(now)
        catalog = FakePodcastCatalog()
        playback = FakePlaybackPersistence()

        val podcast = Podcast(PodcastId("pod-1"), FeedUrl("https://example.com/feed"), "Show", "", true, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)
        catalog.save(podcast, emptyList())
    }

    it("returns empty when there are no episodes") {
        useCase()().shouldBeEmpty()
    }

    it("returns recent unplayed episodes") {
        val podcast = catalog.findAll().first()
        catalog.save(podcast, listOf(episode("ep-1", withinTwoWeeks)))

        useCase()() shouldHaveSize 1
    }

    it("excludes episodes older than two weeks") {
        val podcast = catalog.findAll().first()
        catalog.save(podcast, listOf(
            episode("ep-recent", withinTwoWeeks),
            episode("ep-old", olderThanTwoWeeks),
        ))

        val result = useCase()()
        result shouldHaveSize 1
        result.first().id shouldBe EpisodeId("ep-recent")
    }

    it("excludes played episodes") {
        val podcast = catalog.findAll().first()
        catalog.save(podcast, listOf(
            episode("ep-played", withinTwoWeeks),
            episode("ep-unplayed", withinTwoWeeks),
        ))
        playback.markPlayed(EpisodeId("ep-played"))

        val result = useCase()()
        result shouldHaveSize 1
        result.first().id shouldBe EpisodeId("ep-unplayed")
    }

    it("includes episodes with no playback state") {
        val podcast = catalog.findAll().first()
        catalog.save(podcast, listOf(episode("ep-1", withinTwoWeeks)))

        val result = useCase()()
        result shouldHaveSize 1
    }

    it("returns episodes sorted by publishedAt descending") {
        val podcast = catalog.findAll().first()
        val olderButStillRecent = withinTwoWeeks.plusSeconds(3600)
        catalog.save(podcast, listOf(
            episode("ep-older", withinTwoWeeks),
            episode("ep-newer", olderButStillRecent),
        ))

        val result = useCase()()
        result.map { it.id } shouldBe listOf(EpisodeId("ep-newer"), EpisodeId("ep-older"))

    }
})
