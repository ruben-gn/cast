package application.usecase

import fakes.TestClock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.MarkPlayed
import playback.core.usecase.UpdateProgress
import playback.fakes.FakePlaybackPersistence
import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.usecase.FindEpisode
import podcast.fakes.FakePodcastCatalog
import shared.model.EpisodeId
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class RecordProgressTests : DescribeSpec({
    val now = Instant.parse("2026-09-15T10:00:00Z")
    val episodeId = EpisodeId("ep-1")

    lateinit var clock: TestClock
    lateinit var catalog: FakePodcastCatalog
    lateinit var playback: FakePlaybackPersistence

    fun useCase() = RecordProgress(
        FindEpisode(catalog),
        GetPlaybackState(clock, playback),
        UpdateProgress(clock, playback),
        MarkPlayed(playback),
    )

    suspend fun givenEpisodeLasting(duration: Duration?) {
        catalog.save(
            Podcast(
                PodcastId("pod-1"), FeedUrl("https://feed/rss"), "Pod", "https://img/pod.png",
                true, Instant.EPOCH, Instant.EPOCH, Instant.EPOCH,
            ),
            listOf(
                Episode(
                    id = episodeId,
                    feedGuid = "guid",
                    podcastId = PodcastId("pod-1"),
                    title = "Episode",
                    description = "",
                    audioUrl = "https://cdn/ep-1.mp3",
                    duration = duration,
                    publishedAt = now,
                )
            ),
        )
    }

    beforeEach {
        clock = TestClock(now)
        catalog = FakePodcastCatalog()
        playback = FakePlaybackPersistence()
    }

    describe("recording progress") {
        it("stores the reported position") {
            givenEpisodeLasting(60.minutes)

            useCase()(episodeId, 10.minutes.inWholeMilliseconds, now)

            playback.get(episodeId)!!.progressMs shouldBe 10.minutes.inWholeMilliseconds
        }

        it("leaves an episode unplayed while it is still being listened to") {
            givenEpisodeLasting(60.minutes)

            useCase()(episodeId, 50.minutes.inWholeMilliseconds, now)

            playback.get(episodeId)!!.played shouldBe false
        }

        it("counts an episode as played once only its outro is left") {
            givenEpisodeLasting(47.minutes)

            useCase()(episodeId, 46.minutes.inWholeMilliseconds, now)

            playback.get(episodeId)!!.played shouldBe true
        }
    }

    describe("guarding against reports that finish nothing") {
        it("ignores a replayed position that the episode has already passed") {
            givenEpisodeLasting(47.minutes)
            useCase()(episodeId, 46.minutes.inWholeMilliseconds, now)
            playback.markUnplayed(episodeId)

            useCase()(episodeId, 46.minutes.inWholeMilliseconds, now.minusSeconds(60))

            playback.get(episodeId)!!.played shouldBe false
        }

        it("never finishes an episode of unknown length") {
            givenEpisodeLasting(null)

            useCase()(episodeId, 46.minutes.inWholeMilliseconds, now)

            playback.get(episodeId)!!.played shouldBe false
        }

        it("never finishes an episode that is not in the catalog") {
            useCase()(episodeId, 46.minutes.inWholeMilliseconds, now)

            playback.get(episodeId)!!.played shouldBe false
        }
    }
})
