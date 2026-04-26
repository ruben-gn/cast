package playback.core

import fakes.TestClock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.UpdatePlaybackState
import playback.fakes.FakePlaybackPersistence
import shared.model.EpisodeId
import java.time.Instant
import kotlin.time.Duration.Companion.hours

class PlaybackCoreTests : DescribeSpec({
    val fixedInstant = Instant.parse("2026-04-24T12:00:00Z")

    describe("Playback Domain Hexagon") {
        lateinit var clock: TestClock
        lateinit var persistence: FakePlaybackPersistence
        lateinit var updatePlaybackState: UpdatePlaybackState
        lateinit var getPlaybackState: GetPlaybackState

        beforeEach {
            clock = TestClock(fixedInstant)
            persistence = FakePlaybackPersistence()
            updatePlaybackState = UpdatePlaybackState(clock, persistence)
            getPlaybackState = GetPlaybackState(clock, persistence)
        }

        it("should update and retrieve the playback state for an episode") {
            val episodeId = "ep-123"
            val progressMs = 5000L

            updatePlaybackState(episodeId, progressMs)

            val retrieved = getPlaybackState(episodeId)
            retrieved.episodeId shouldBe EpisodeId(episodeId)
            retrieved.progressMs shouldBe progressMs
            retrieved.updatedAt shouldBe fixedInstant
        }

        it("should return no progress when retrieving state for an episode with no progress") {
            val retrieved = getPlaybackState("non-existent")
            retrieved.episodeId shouldBe EpisodeId("non-existent")
            retrieved.progressMs shouldBe 0
            retrieved.updatedAt shouldBe fixedInstant
        }

        it("should overwrite existing state and update the timestamp when ticking") {
            val episodeId = "ep-123"

            updatePlaybackState(episodeId, 1000L)
            
            clock.tick(1.hours)

            updatePlaybackState(episodeId, 2000L)

            val finalState = getPlaybackState(episodeId)
            finalState.progressMs shouldBe 2000L
            finalState.updatedAt shouldBe Instant.parse("2026-04-24T13:00:00Z")
        }
    }
})