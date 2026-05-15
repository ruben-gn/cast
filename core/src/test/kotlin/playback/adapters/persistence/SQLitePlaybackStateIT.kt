package playback.adapters.persistence

import configuration.CREATE_PLAYBACK_STATE_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import playback.core.models.PlaybackState
import shared.model.EpisodeId
import java.sql.DriverManager
import java.time.Instant

class SQLitePlaybackStateIT : DescribeSpec({

    lateinit var db: SingleConnectionProvider
    lateinit var persistence: SQLitePlaybackState

    beforeEach {
        db = SingleConnectionProvider(DriverManager.getConnection("jdbc:sqlite::memory:"))
        persistence = SQLitePlaybackState(db)
        db.withConnection { conn ->
            conn.createStatement().use { it.execute(CREATE_PLAYBACK_STATE_TABLE) }
        }
    }

    afterEach { db.close() }

    describe("get") {
        it("returns null for an unknown episode") {
            persistence.get(EpisodeId("unknown")) shouldBe null
        }
    }

    describe("updateProgress") {
        it("creates a row with the correct values and played = false") {
            val episodeId = EpisodeId("ep-1")
            val updatedAt = Instant.parse("2024-01-15T10:30:00Z")

            persistence.updateProgress(episodeId, 5000, updatedAt)

            persistence.get(episodeId) shouldBe PlaybackState(
                episodeId = episodeId,
                progressMs = 5000,
                updatedAt = updatedAt,
                played = false,
            )
        }

        it("does not reset played when called after markPlayed") {
            val episodeId = EpisodeId("ep-1")
            persistence.updateProgress(episodeId, 1000, Instant.parse("2024-01-15T10:00:00Z"))
            persistence.markPlayed(episodeId)

            persistence.updateProgress(episodeId, 9000, Instant.parse("2024-01-15T10:30:00Z"))

            val state = persistence.get(episodeId)!!
            state.progressMs shouldBe 9000
            state.played shouldBe true
        }
    }

    describe("markPlayed") {
        it("creates a row with played = true") {
            persistence.markPlayed(EpisodeId("ep-1"))

            persistence.get(EpisodeId("ep-1"))!!.played shouldBe true
        }

        it("sets played = true on an existing row") {
            val episodeId = EpisodeId("ep-1")
            persistence.updateProgress(episodeId, 5000, Instant.parse("2024-01-15T10:30:00Z"))

            persistence.markPlayed(episodeId)

            persistence.get(episodeId)!!.played shouldBe true
        }
    }

    describe("resetProgress") {
        it("sets played = false and updates progress on an existing played row") {
            val episodeId = EpisodeId("ep-1")
            persistence.updateProgress(episodeId, 5000, Instant.parse("2024-01-15T10:00:00Z"))
            persistence.markPlayed(episodeId)

            persistence.resetProgress(episodeId, 30_000, Instant.parse("2024-01-15T11:00:00Z"))

            val state = persistence.get(episodeId)!!
            state.played shouldBe false
            state.progressMs shouldBe 30_000
        }

        it("creates a new row with played = false when no prior state exists") {
            val episodeId = EpisodeId("ep-new")

            persistence.resetProgress(episodeId, 0, Instant.parse("2024-01-15T10:00:00Z"))

            val state = persistence.get(episodeId)!!
            state.played shouldBe false
            state.progressMs shouldBe 0
        }
    }

    describe("markAllPlayed") {
        it("marks all given episodes as played") {
            val ids = listOf(EpisodeId("ep-1"), EpisodeId("ep-2"), EpisodeId("ep-3"))
            ids.forEach { persistence.updateProgress(it, 5000, Instant.parse("2024-01-15T10:00:00Z")) }

            persistence.markAllPlayed(ids)

            ids.forEach { persistence.get(it)!!.played shouldBe true }
        }

        it("creates rows for episodes with no prior progress") {
            val ids = listOf(EpisodeId("ep-new-1"), EpisodeId("ep-new-2"))

            persistence.markAllPlayed(ids)

            ids.forEach { persistence.get(it)!!.played shouldBe true }
        }

        it("is a no-op for an empty list") {
            persistence.markAllPlayed(emptyList())
        }
    }
})
