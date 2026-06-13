package application.usecase

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import playback.core.usecase.GetPlaybackState
import playback.fakes.FakePlaybackPersistence
import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.usecase.FindEpisode
import podcast.core.usecase.GetPodcast
import podcast.fakes.FakePodcastCatalog
import shared.model.EpisodeId
import java.time.Clock
import java.time.Instant

class GetEpisodeDetailTests : DescribeSpec({
    val podcastId = PodcastId("pod-1")
    val episodeId = EpisodeId("ep-1")

    lateinit var catalog: FakePodcastCatalog
    lateinit var playback: FakePlaybackPersistence

    fun useCase() = GetEpisodeDetail(
        findEpisode = FindEpisode(catalog),
        getPlaybackState = GetPlaybackState(Clock.systemUTC(), playback),
        getPodcast = GetPodcast(catalog),
    )

    val podcast = Podcast(podcastId, FeedUrl("https://example.com/feed"), "Test Show", "https://img/show.png", true, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)

    val episode = Episode(
        id = episodeId, feedGuid = "ep-1", podcastId = podcastId,
        title = "Episode 1", description = "Desc",
        audioUrl = "https://cdn/ep1.mp3", duration = null, publishedAt = null,
    )

    beforeEach {
        catalog = FakePodcastCatalog()
        playback = FakePlaybackPersistence()
    }

    it("returns null for an unknown episode") {
        useCase()(episodeId) shouldBe null
    }

    it("returns null when the podcast is missing") {
        val orphanPodcast = podcast.copy(id = PodcastId("gone"))
        val orphanEpisode = episode.copy(podcastId = PodcastId("gone"))
        catalog.save(orphanPodcast, listOf(orphanEpisode))
        catalog.delete(PodcastId("gone"))

        useCase()(orphanEpisode.id) shouldBe null
    }

    it("returns the episode with playback state and podcast info") {
        catalog.save(podcast, listOf(episode))
        playback.updateProgress(episodeId, 30000, Instant.EPOCH)

        val result = useCase()(episodeId)!!
        result.episode.id shouldBe episodeId
        result.progressMs shouldBe 30000
        result.played shouldBe false
        result.podcastName shouldBe "Test Show"
        result.podcastImage shouldBe "https://img/show.png"
    }

    it("reflects the played state") {
        catalog.save(podcast, listOf(episode))
        playback.markPlayed(episodeId)

        val result = useCase()(episodeId)!!
        result.played shouldBe true
    }
})
