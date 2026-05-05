package playback.core

import fakes.TestClock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.MarkPlayed
import playback.core.usecase.UpdateProgress
import playback.fakes.FakePlaybackPersistence
import shared.model.EpisodeId
import java.time.Instant
import kotlin.time.Duration.Companion.hours

class PlaybackCoreTests : DescribeSpec({
    val fixedInstant = Instant.parse("2026-04-24T12:00:00Z")

    describe("Playback Domain Hexagon") {
        lateinit var clock: TestClock
        lateinit var persistence: FakePlaybackPersistence
        lateinit var updateProgress: UpdateProgress
        lateinit var getPlaybackState: GetPlaybackState
        lateinit var markPlayed: MarkPlayed

        beforeEach {
            clock = TestClock(fixedInstant)
            persistence = FakePlaybackPersistence()
            updateProgress = UpdateProgress(clock, persistence)
            getPlaybackState = GetPlaybackState(clock, persistence)
            markPlayed = MarkPlayed(persistence)
        }

        it("should update and retrieve the playback state for an episode") {
            val episodeId = "ep-123"
            val progressMs = 5000L

            updateProgress(episodeId, progressMs)

            val retrieved = getPlaybackState(episodeId)
            retrieved.episodeId shouldBe EpisodeId(episodeId)
            retrieved.progressMs shouldBe progressMs
            retrieved.updatedAt shouldBe fixedInstant
            retrieved.played shouldBe false
        }

        it("should return no progress when retrieving state for an episode with no progress") {
            val retrieved = getPlaybackState("non-existent")
            retrieved.episodeId shouldBe EpisodeId("non-existent")
            retrieved.progressMs shouldBe 0
            retrieved.updatedAt shouldBe fixedInstant
            retrieved.played shouldBe false
        }

        it("should overwrite existing state and update the timestamp when ticking") {
            val episodeId = "ep-123"

            updateProgress(episodeId, 1000L)

            clock.tick(1.hours)

            updateProgress(episodeId, 2000L)

            val finalState = getPlaybackState(episodeId)
            finalState.progressMs shouldBe 2000L
            finalState.updatedAt shouldBe Instant.parse("2026-04-24T13:00:00Z")
        }

        it("should mark an episode as played") {
            val episodeId = "ep-123"

            updateProgress(episodeId, 5000L)
            markPlayed(episodeId)

            val state = getPlaybackState(episodeId)
            state.played shouldBe true
            state.progressMs shouldBe 5000L
        }

        it("should mark an episode as played even with no prior progress") {
            markPlayed("ep-456")

            val state = getPlaybackState("ep-456")
            state.played shouldBe true
            state.progressMs shouldBe 0
        }

        it("should not reset played when progress update arrives after markPlayed") {
            val episodeId = "ep-123"
            updateProgress(episodeId, 5000L)
            markPlayed(episodeId)
            updateProgress(episodeId, 9000L)

            val state = getPlaybackState(episodeId)
            state.played shouldBe true
            state.progressMs shouldBe 9000L
        }
    }
})