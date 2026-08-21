package application.usecase

import application.usecase.GetPodcastDetail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import playback.fakes.FakePlaybackPersistence
import playback.core.usecase.GetPlaybackStates
import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.models.SeriesRule
import podcast.core.usecase.GetPodcast
import podcast.core.usecase.ListEpisodes
import podcast.core.usecase.ListSeriesRules
import podcast.fakes.FakePodcastCatalog
import podcast.fakes.FakeSeriesRulePersistence
import settings.core.models.Settings
import settings.core.usecase.GetSettings
import settings.fakes.FakeSettingsPersistence
import shared.model.EpisodeId
import java.time.Instant

class GetPodcastDetailTests : DescribeSpec({
    val podcastId = PodcastId("pod-1")
    val ep1 = EpisodeId("ep-1")
    val ep2 = EpisodeId("ep-2")
    val podcast = Podcast(podcastId, FeedUrl("https://example.com/feed"), "Test Show", "", true, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)

    lateinit var catalog: FakePodcastCatalog
    lateinit var playback: FakePlaybackPersistence
    lateinit var settingsPersistence: FakeSettingsPersistence
    lateinit var seriesRulePersistence: FakeSeriesRulePersistence

    fun useCase() = GetPodcastDetail(
        getPodcast = GetPodcast(catalog),
        listEpisodes = ListEpisodes(catalog),
        getPlaybackStates = GetPlaybackStates(playback),
        getSettings = GetSettings(settingsPersistence),
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
        playback = FakePlaybackPersistence()
        settingsPersistence = FakeSettingsPersistence()
        seriesRulePersistence = FakeSeriesRulePersistence()

        catalog.save(podcast, listOf(episode(ep1), episode(ep2)))
    }

    describe("hidePlayed = false") {
        it("returns all episodes regardless of played state") {
            settingsPersistence.current = Settings(hidePlayed = false)
            playback.markPlayed(ep1)

            val result = useCase()(podcastId)!!
            result.episodes shouldHaveSize 2
        }

        it("includes played flag on each episode") {
            playback.markPlayed(ep1)

            val result = useCase()(podcastId)!!
            result.episodes.first { it.episode.id == ep1 }.played shouldBe true
            result.episodes.first { it.episode.id == ep2 }.played shouldBe false
        }
    }

    describe("hidePlayed = true") {
        beforeEach { settingsPersistence.current = Settings(hidePlayed = true) }

        it("filters out played episodes") {
            playback.markPlayed(ep1)

            val result = useCase()(podcastId)!!
            result.episodes shouldHaveSize 1
            result.episodes[0].episode.id shouldBe ep2
        }

        it("returns all episodes when none are played") {
            val result = useCase()(podcastId)!!
            result.episodes shouldHaveSize 2
        }

        it("returns empty list when all episodes are played") {
            playback.markPlayed(ep1)
            playback.markPlayed(ep2)

            val result = useCase()(podcastId)!!
            result.episodes shouldHaveSize 0
        }
    }

    describe("series names") {
        it("sets the series name on episodes whose title matches a rule") {
            catalog.save(podcast, listOf(episode(ep1).copy(title = "The Divided Dial: Part 1"), episode(ep2)))
            seriesRulePersistence.add(SeriesRule(podcastId, "The Divided Dial"))

            val result = useCase()(podcastId)!!
            result.episodes.first { it.episode.id == ep1 }.seriesName shouldBe "The Divided Dial"
        }

        it("leaves the series name null on episodes matching no rule") {
            seriesRulePersistence.add(SeriesRule(podcastId, "The Divided Dial"))

            val result = useCase()(podcastId)!!
            result.episodes.forAll { it.seriesName shouldBe null }
        }
    }

    it("returns null for an unknown podcast") {
        useCase()(PodcastId("unknown")) shouldBe null
    }
})
