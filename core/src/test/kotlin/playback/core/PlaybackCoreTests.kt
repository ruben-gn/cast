package playback.core

import fakes.TestClock
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.MarkAllPlayed
import playback.core.usecase.MarkPlayed
import playback.core.usecase.MarkUnplayed
import playback.core.usecase.StartPlayback
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
        lateinit var markUnplayed: MarkUnplayed
        lateinit var markAllPlayed: MarkAllPlayed
        lateinit var startPlayback: StartPlayback

        beforeEach {
            clock = TestClock(fixedInstant)
            persistence = FakePlaybackPersistence()
            updateProgress = UpdateProgress(clock, persistence)
            getPlaybackState = GetPlaybackState(clock, persistence)
            markPlayed = MarkPlayed(persistence)
            markUnplayed = MarkUnplayed(persistence)
            markAllPlayed = MarkAllPlayed(persistence)
            startPlayback = StartPlayback(clock, persistence)
        }

        it("updates and retrieves the playback state for an episode") {
            val episodeId = EpisodeId("ep-123")
            val progressMs = 5000L

            updateProgress(episodeId, progressMs, updatedAt = null)

            val retrieved = getPlaybackState(episodeId)
            retrieved.episodeId shouldBe episodeId
            retrieved.progressMs shouldBe progressMs
            retrieved.updatedAt shouldBe fixedInstant
            retrieved.played shouldBe false
        }

        it("returns no progress for an episode with no prior state") {
            val retrieved = getPlaybackState(EpisodeId("non-existent"))
            retrieved.episodeId shouldBe EpisodeId("non-existent")
            retrieved.progressMs shouldBe 0
            retrieved.updatedAt shouldBe fixedInstant
            retrieved.played shouldBe false
        }

        it("overwrites existing state and updates the timestamp on subsequent progress") {
            val episodeId = EpisodeId("ep-123")

            updateProgress(episodeId, 1000L, updatedAt = null)

            clock.tick(1.hours)

            updateProgress(episodeId, 2000L, updatedAt = null)

            val finalState = getPlaybackState(episodeId)
            finalState.progressMs shouldBe 2000L
            finalState.updatedAt shouldBe Instant.parse("2026-04-24T13:00:00Z")
        }

        it("marks an episode as played") {
            val episodeId = EpisodeId("ep-123")

            updateProgress(episodeId, 5000L, updatedAt = null)
            markPlayed(episodeId)

            val state = getPlaybackState(episodeId)
            state.played shouldBe true
            state.progressMs shouldBe 5000L
        }

        it("marks a played episode as unplayed") {
            val episodeId = EpisodeId("ep-123")
            updateProgress(episodeId, 5000L, updatedAt = null)
            markPlayed(episodeId)

            markUnplayed(episodeId)

            getPlaybackState(episodeId).played shouldBe false
        }

        it("marks an episode as unplayed even with no prior progress") {
            val episodeId = EpisodeId("ep-456")
            markUnplayed(episodeId)

            val state = getPlaybackState(episodeId)
            state.played shouldBe false
            state.progressMs shouldBe 0
        }

        it("marks an episode as played even with no prior progress") {
            val episodeId = EpisodeId("ep-456")
            markPlayed(episodeId)

            val state = getPlaybackState(episodeId)
            state.played shouldBe true
            state.progressMs shouldBe 0
        }

        it("marks all episodes as played") {
            val ids = listOf(EpisodeId("ep-1"), EpisodeId("ep-2"), EpisodeId("ep-3"))
            ids.forEach { updateProgress(it, 1000L, updatedAt = null) }

            markAllPlayed(ids)

            ids.forEach { getPlaybackState(it).played shouldBe true }
        }

        it("startPlayback resets played to false and sets position") {
            val episodeId = EpisodeId("ep-1")
            markPlayed(episodeId)

            startPlayback(episodeId, startPositionMs = 30_000L)

            val state = getPlaybackState(episodeId)
            state.played shouldBe false
            state.progressMs shouldBe 30_000L
        }

        it("startPlayback at position 0 resets a played episode") {
            val episodeId = EpisodeId("ep-1")
            updateProgress(episodeId, 45_000L, updatedAt = null)
            markPlayed(episodeId)

            startPlayback(episodeId, startPositionMs = 0L)

            val state = getPlaybackState(episodeId)
            state.played shouldBe false
            state.progressMs shouldBe 0L
        }

        it("does not reset played when progress update arrives after markPlayed") {
            val episodeId = EpisodeId("ep-123")
            updateProgress(episodeId, 5000L, updatedAt = null)
            markPlayed(episodeId)
            clock.tick(1.hours)
            updateProgress(episodeId, 9000L, updatedAt = null)

            val state = getPlaybackState(episodeId)
            state.played shouldBe true
            state.progressMs shouldBe 9000L
        }

        it("stamps the clock's instant when updatedAt is not provided") {
            val episodeId = EpisodeId("ep-123")

            updateProgress(episodeId, 5000L, updatedAt = null)

            getPlaybackState(episodeId).updatedAt shouldBe fixedInstant
        }

        it("uses the caller-provided updatedAt instead of the clock") {
            val episodeId = EpisodeId("ep-123")
            val explicit = Instant.parse("2020-01-01T00:00:00Z")

            updateProgress(episodeId, 5000L, updatedAt = explicit)

            getPlaybackState(episodeId).updatedAt shouldBe explicit
        }
    }
})
