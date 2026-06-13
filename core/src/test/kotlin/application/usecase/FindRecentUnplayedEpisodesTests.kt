package application.usecase

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
import podcast.core.usecase.ListPodcasts
import podcast.fakes.FakePodcastCatalog
import settings.core.models.Settings
import settings.core.usecase.GetSettings
import settings.fakes.FakeSettingsPersistence
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
    lateinit var settingsPersistence: FakeSettingsPersistence

    fun useCase() = FindRecentUnplayedEpisodes(
        clock,
        FindRecentEpisodes(catalog),
        GetPlaybackStates(playback),
        ListPodcasts(catalog),
        GetSettings(settingsPersistence),
    )

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
        settingsPersistence = FakeSettingsPersistence()

        val podcast = Podcast(PodcastId("pod-1"), FeedUrl("https://example.com/feed"), "Test Show", "https://img/show.png", true, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)
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
        result.first().episode.id shouldBe EpisodeId("ep-recent")
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
        result.first().episode.id shouldBe EpisodeId("ep-unplayed")
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
        result.map { it.episode.id } shouldBe listOf(EpisodeId("ep-newer"), EpisodeId("ep-older"))
    }

    it("populates podcast name and image on each episode") {
        val podcast = catalog.findAll().first()
        catalog.save(podcast, listOf(episode("ep-1", withinTwoWeeks)))

        val result = useCase()()
        result[0].podcastName shouldBe "Test Show"
        result[0].podcastImage shouldBe "https://img/show.png"
    }

    describe("recentListeningOnly = true") {
        beforeEach { settingsPersistence.current = Settings(recentListeningOnly = true) }

        it("excludes episodes from non-listening podcasts") {
            val nonListeningPodcast = Podcast(
                PodcastId("pod-2"), FeedUrl("https://example.com/feed2"),
                "Other Show", "", false, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH,
            )
            val nonListeningEp = Episode(
                id = EpisodeId("ep-other"), feedGuid = "ep-other",
                podcastId = PodcastId("pod-2"), title = "Other Ep",
                description = "", audioUrl = "https://cdn/other.mp3",
                duration = null, publishedAt = withinTwoWeeks,
            )
            catalog.save(nonListeningPodcast, listOf(nonListeningEp))

            val listeningPodcast = catalog.findAll().first { it.id == PodcastId("pod-1") }
            catalog.save(listeningPodcast, listOf(episode("ep-listening", withinTwoWeeks)))

            val result = useCase()()
            result shouldHaveSize 1
            result[0].episode.id shouldBe EpisodeId("ep-listening")
        }

        it("includes episodes from listening podcasts") {
            val podcast = catalog.findAll().first()
            catalog.save(podcast, listOf(episode("ep-1", withinTwoWeeks)))

            useCase()() shouldHaveSize 1
        }
    }
})
