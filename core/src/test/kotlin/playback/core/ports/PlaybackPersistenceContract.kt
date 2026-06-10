package playback.core.ports

import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.describeSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import playback.core.models.PlaybackState
import shared.model.EpisodeId
import java.time.Instant

// updatedAt after mark*/markAll* is deliberately unasserted: SQLite stamps its own clock there
// while updateProgress takes the caller's — unify before tightening this contract.
fun playbackPersistenceContract(persistenceProvider: () -> PlaybackPersistence): TestFactory = describeSpec {

    describe("get") {
        it("returns null for an unknown episode") {
            val persistence = persistenceProvider()
            persistence.get(EpisodeId("unknown")) shouldBe null
        }
    }

    describe("updateProgress") {
        it("creates a row with the correct values and played = false") {
            val persistence = persistenceProvider()
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
            val persistence = persistenceProvider()
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
            val persistence = persistenceProvider()
            persistence.markPlayed(EpisodeId("ep-1"))

            persistence.get(EpisodeId("ep-1"))!!.played shouldBe true
        }

        it("sets played = true on an existing row") {
            val persistence = persistenceProvider()
            val episodeId = EpisodeId("ep-1")
            persistence.updateProgress(episodeId, 5000, Instant.parse("2024-01-15T10:30:00Z"))

            persistence.markPlayed(episodeId)

            persistence.get(episodeId)!!.played shouldBe true
        }
    }

    describe("markUnplayed") {
        it("sets played = false on a played episode") {
            val persistence = persistenceProvider()
            val episodeId = EpisodeId("ep-1")
            persistence.markPlayed(episodeId)

            persistence.markUnplayed(episodeId)

            persistence.get(episodeId)!!.played shouldBe false
        }

        it("creates an unplayed row when no prior state exists") {
            val persistence = persistenceProvider()
            val episodeId = EpisodeId("ep-new")

            persistence.markUnplayed(episodeId)

            persistence.get(episodeId)!!.played shouldBe false
        }
    }

    describe("resetProgress") {
        it("sets played = false and updates progress on an existing played row") {
            val persistence = persistenceProvider()
            val episodeId = EpisodeId("ep-1")
            persistence.updateProgress(episodeId, 5000, Instant.parse("2024-01-15T10:00:00Z"))
            persistence.markPlayed(episodeId)

            persistence.resetProgress(episodeId, 30_000, Instant.parse("2024-01-15T11:00:00Z"))

            val state = persistence.get(episodeId)!!
            state.played shouldBe false
            state.progressMs shouldBe 30_000
        }

        it("creates a new row with played = false when no prior state exists") {
            val persistence = persistenceProvider()
            val episodeId = EpisodeId("ep-new")

            persistence.resetProgress(episodeId, 0, Instant.parse("2024-01-15T10:00:00Z"))

            val state = persistence.get(episodeId)!!
            state.played shouldBe false
            state.progressMs shouldBe 0
        }
    }

    describe("markAllPlayed") {
        it("marks all given episodes as played") {
            val persistence = persistenceProvider()
            val ids = listOf(EpisodeId("ep-1"), EpisodeId("ep-2"), EpisodeId("ep-3"))
            ids.forEach { persistence.updateProgress(it, 5000, Instant.parse("2024-01-15T10:00:00Z")) }

            persistence.markAllPlayed(ids)

            ids.forEach { persistence.get(it)!!.played shouldBe true }
        }

        it("creates rows for episodes with no prior progress") {
            val persistence = persistenceProvider()
            val ids = listOf(EpisodeId("ep-new-1"), EpisodeId("ep-new-2"))

            persistence.markAllPlayed(ids)

            ids.forEach { persistence.get(it)!!.played shouldBe true }
        }

        it("is a no-op for an empty list") {
            val persistence = persistenceProvider()
            persistence.markAllPlayed(emptyList())
        }
    }

    describe("getAll") {
        it("returns states for the requested ids, omitting ids with no state") {
            val persistence = persistenceProvider()
            val known = EpisodeId("ep-known")
            val other = EpisodeId("ep-other")
            persistence.updateProgress(known, 5000, Instant.parse("2024-01-15T10:00:00Z"))
            persistence.updateProgress(other, 7000, Instant.parse("2024-01-15T10:00:00Z"))

            val result = persistence.getAll(listOf(known, EpisodeId("ep-missing")))

            result shouldContainKey known
            result shouldNotContainKey other
            result shouldNotContainKey EpisodeId("ep-missing")
            result[known]!!.progressMs shouldBe 5000
        }

        it("returns an empty map for an empty list") {
            val persistence = persistenceProvider()
            persistence.updateProgress(EpisodeId("ep-1"), 5000, Instant.parse("2024-01-15T10:00:00Z"))

            persistence.getAll(emptyList()).shouldBeEmpty()
        }
    }

    describe("removeAll") {
        it("removes state for the given episodes, leaving others") {
            val persistence = persistenceProvider()
            val remove = EpisodeId("ep-remove")
            val keep = EpisodeId("ep-keep")
            persistence.updateProgress(remove, 5000, Instant.parse("2024-01-15T10:00:00Z"))
            persistence.updateProgress(keep, 7000, Instant.parse("2024-01-15T10:00:00Z"))

            persistence.removeAll(listOf(remove))

            persistence.get(remove) shouldBe null
            persistence.get(keep)!!.progressMs shouldBe 7000
        }

        it("is a no-op for an empty list") {
            val persistence = persistenceProvider()
            persistence.updateProgress(EpisodeId("ep-1"), 5000, Instant.parse("2024-01-15T10:00:00Z"))

            persistence.removeAll(emptyList())

            persistence.get(EpisodeId("ep-1"))!!.progressMs shouldBe 5000
        }
    }
}
