package application.usecase

import application.usecase.GetPodcastDetail
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import playback.fakes.FakePlaybackPersistence
import playback.core.usecase.GetPlaybackStates
import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.usecase.GetPodcast
import podcast.core.usecase.ListEpisodes
import podcast.fakes.FakePodcastCatalog
import settings.core.models.Settings
import settings.core.usecase.GetSettings
import settings.fakes.FakeSettingsPersistence
import shared.model.EpisodeId
import java.time.Instant

class GetPodcastDetailTests : DescribeSpec({
    val podcastId = PodcastId("pod-1")
    val ep1 = EpisodeId("ep-1")
    val ep2 = EpisodeId("ep-2")

    lateinit var catalog: FakePodcastCatalog
    lateinit var playback: FakePlaybackPersistence
    lateinit var settingsPersistence: FakeSettingsPersistence

    fun useCase() = GetPodcastDetail(
        getPodcast = GetPodcast(catalog),
        listEpisodes = ListEpisodes(catalog),
        getPlaybackStates = GetPlaybackStates(playback),
        getSettings = GetSettings(settingsPersistence),
    )

    fun episode(id: EpisodeId) = Episode(
        id = id,
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

        val podcast = Podcast(podcastId, FeedUrl("https://example.com/feed"), "Test Show", "", Instant.EPOCH, Instant.EPOCH)
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

    it("returns null for an unknown podcast") {
        useCase()(PodcastId("unknown")) shouldBe null
    }
})
