package application.usecase

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
import podcast.core.usecase.FindEpisode
import podcast.core.usecase.ListPodcasts
import podcast.fakes.FakePodcastCatalog
import queue.core.model.Queue
import queue.core.usecase.GetQueue
import queue.fakes.FakeQueuePersistence
import series.core.models.SeriesRule
import series.core.usecase.ListSeriesRules
import series.fakes.FakeSeriesRulePersistence
import settings.core.models.Settings
import settings.core.usecase.GetSettings
import settings.fakes.FakeSettingsPersistence
import shared.model.EpisodeId
import java.time.Instant

class GetQueueDetailTests : DescribeSpec({
    val podcastId = PodcastId("pod-1")
    val ep1 = EpisodeId("ep-1")
    val ep2 = EpisodeId("ep-2")

    lateinit var catalog: FakePodcastCatalog
    lateinit var queue: FakeQueuePersistence
    lateinit var playback: FakePlaybackPersistence
    lateinit var settingsPersistence: FakeSettingsPersistence
    lateinit var seriesRulePersistence: FakeSeriesRulePersistence

    fun useCase() = GetQueueDetail(
        getQueue = GetQueue(queue),
        findEpisode = FindEpisode(catalog),
        getPlaybackStates = GetPlaybackStates(playback),
        getSettings = GetSettings(settingsPersistence),
        listPodcasts = ListPodcasts(catalog),
        listSeriesRules = ListSeriesRules(seriesRulePersistence),
    )

    fun episode(id: EpisodeId) = Episode(
        id = id,
        feedGuid = id.value,
        podcastId = podcastId,
        title = "Episode ${id.value}",
        description = "",
        audioUrl = "https://cdn/${id.value}.mp3",
        duration = null,
        publishedAt = null,
    )

    beforeEach {
        catalog = FakePodcastCatalog()
        queue = FakeQueuePersistence()
        playback = FakePlaybackPersistence()
        settingsPersistence = FakeSettingsPersistence()
        seriesRulePersistence = FakeSeriesRulePersistence()

        val podcast = Podcast(podcastId, FeedUrl("https://example.com/feed"), "Test Show", "", true, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)
        catalog.save(podcast, listOf(episode(ep1), episode(ep2)))
    }

    it("returns empty list when queue is empty") {
        useCase()().shouldBeEmpty()
    }

    it("returns episodes in queue order with playback state") {
        queue.save(Queue(listOf(ep2, ep1)))
        playback.updateProgress(ep1, 5000, Instant.EPOCH)

        val result = useCase()()
        result.map { it.episode.id } shouldBe listOf(ep2, ep1)
        result.first { it.episode.id == ep1 }.progressMs shouldBe 5000
        result.first { it.episode.id == ep2 }.progressMs shouldBe 0
    }

    it("defaults to progressMs=0 and played=false for episodes with no playback state") {
        queue.save(Queue(listOf(ep1)))

        val result = useCase()()
        result[0].progressMs shouldBe 0
        result[0].played shouldBe false
    }

    it("skips episodes whose id is no longer in the catalog") {
        val stale = EpisodeId("deleted-ep")
        queue.save(Queue(listOf(stale, ep1)))

        val result = useCase()()
        result shouldHaveSize 1
        result[0].episode.id shouldBe ep1
    }

    describe("hidePlayed = false") {
        it("returns all episodes including played ones") {
            queue.save(Queue(listOf(ep1, ep2)))
            playback.markPlayed(ep1)

            useCase()() shouldHaveSize 2
        }

        it("includes played flag on each episode") {
            queue.save(Queue(listOf(ep1, ep2)))
            playback.markPlayed(ep1)

            val result = useCase()()
            result.first { it.episode.id == ep1 }.played shouldBe true
            result.first { it.episode.id == ep2 }.played shouldBe false
        }
    }

    it("populates podcast name and image on each episode") {
        queue.save(Queue(listOf(ep1)))

        val result = useCase()()
        result[0].podcastName shouldBe "Test Show"
        result[0].podcastImage shouldBe ""
    }

    it("sets the series name when a rule matches the episode title") {
        val podcast = catalog.findAll().first()
        catalog.save(podcast, listOf(episode(ep1).copy(title = "The Divided Dial: Part 1"), episode(ep2)))
        seriesRulePersistence.add(SeriesRule(podcastId, "The Divided Dial"))
        queue.save(Queue(listOf(ep1, ep2)))

        val result = useCase()()
        result.first { it.episode.id == ep1 }.seriesName shouldBe "The Divided Dial"
        result.first { it.episode.id == ep2 }.seriesName shouldBe null
    }

    describe("hidePlayed = true") {
        beforeEach { settingsPersistence.current = Settings(hidePlayed = true) }

        it("filters out played episodes") {
            queue.save(Queue(listOf(ep1, ep2)))
            playback.markPlayed(ep1)

            val result = useCase()()
            result shouldHaveSize 1
            result[0].episode.id shouldBe ep2
        }

        it("returns all episodes when none are played") {
            queue.save(Queue(listOf(ep1, ep2)))

            useCase()() shouldHaveSize 2
        }

        it("returns empty list when all queued episodes are played") {
            queue.save(Queue(listOf(ep1, ep2)))
            playback.markPlayed(ep1)
            playback.markPlayed(ep2)

            useCase()().shouldBeEmpty()
        }
    }
})
