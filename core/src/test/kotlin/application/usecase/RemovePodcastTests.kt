package application.usecase

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import playback.core.usecase.RemovePlaybackStates
import playback.fakes.FakePlaybackPersistence
import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.usecase.DeletePodcast
import podcast.core.usecase.ListEpisodes
import podcast.fakes.FakePodcastCatalog
import podcast.fakes.FakeSeriesRulePersistence
import queue.core.model.Queue
import queue.core.usecase.DequeueEpisodes
import queue.fakes.FakeQueuePersistence
import shared.model.EpisodeId
import java.time.Instant

class RemovePodcastTests : DescribeSpec({
    val podcastId = PodcastId("pod-1")
    val ep1 = EpisodeId("ep-1")
    val ep2 = EpisodeId("ep-2")
    val otherEp = EpisodeId("other-ep")

    lateinit var catalog: FakePodcastCatalog
    lateinit var seriesRules: FakeSeriesRulePersistence
    lateinit var queue: FakeQueuePersistence
    lateinit var playback: FakePlaybackPersistence

    fun useCase() = RemovePodcast(
        listEpisodes = ListEpisodes(catalog),
        deletePodcast = DeletePodcast(catalog, seriesRules),
        removePlaybackStates = RemovePlaybackStates(playback),
        dequeueEpisodes = DequeueEpisodes(queue),
    )

    fun episode(id: EpisodeId, owner: PodcastId = podcastId) = Episode(
        id = id,
        feedGuid = id.value,
        podcastId = owner,
        title = "Episode ${id.value}",
        description = "",
        audioUrl = "https://cdn/${id.value}.mp3",
        duration = null,
        publishedAt = null,
    )

    beforeEach {
        catalog = FakePodcastCatalog()
        seriesRules = FakeSeriesRulePersistence()
        queue = FakeQueuePersistence()
        playback = FakePlaybackPersistence()

        val podcast = Podcast(podcastId, FeedUrl("https://example.com/feed"), "Test Show", "", true, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)
        catalog.save(podcast, listOf(episode(ep1), episode(ep2)))
    }

    it("deletes the podcast and clears playback state for its episodes") {
        playback.markPlayed(ep1)
        playback.updateProgress(ep2, 5000, Instant.EPOCH)

        useCase()(podcastId) shouldBe true

        catalog.findById(podcastId) shouldBe null
        playback.get(ep1) shouldBe null
        playback.get(ep2) shouldBe null
    }

    it("removes the podcast's episodes from the queue, leaving others") {
        queue.save(Queue(listOf(ep1, otherEp, ep2)))

        useCase()(podcastId) shouldBe true

        queue.get().episodeIds shouldContainExactly listOf(otherEp)
    }

    it("does not touch playback state for episodes of other podcasts") {
        playback.markPlayed(otherEp)

        useCase()(podcastId) shouldBe true

        playback.get(otherEp)?.played shouldBe true
    }

    it("returns false and changes nothing when the podcast does not exist") {
        queue.save(Queue(listOf(otherEp)))
        playback.markPlayed(otherEp)

        useCase()(PodcastId("missing")) shouldBe false

        queue.get().episodeIds shouldContainExactly listOf(otherEp)
        playback.get(otherEp)?.played shouldBe true
    }
})
