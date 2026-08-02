package podcast.core.ports

import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.describeSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import podcast.core.models.PodcastId
import podcast.core.models.SeriesRule

fun seriesRulePersistenceContract(persistenceProvider: () -> SeriesRulePersistence): TestFactory = describeSpec {

    describe("findAll") {
        it("starts empty") {
            val persistence = persistenceProvider()
            persistence.findAll() shouldBe emptyList()
        }
    }

    describe("add") {
        it("returns an added rule from findAll") {
            val persistence = persistenceProvider()
            val rule = SeriesRule(PodcastId("podcast-1"), "The Divided Dial")

            persistence.add(rule)

            persistence.findAll() shouldBe listOf(rule)
        }

        it("keeps a single rule when adding the same podcastId and name twice") {
            val persistence = persistenceProvider()
            val rule = SeriesRule(PodcastId("podcast-1"), "The Divided Dial")

            persistence.add(rule)
            persistence.add(rule)

            persistence.findAll() shouldBe listOf(rule)
        }

        it("keeps rules for different podcasts") {
            val persistence = persistenceProvider()
            val ruleA = SeriesRule(PodcastId("podcast-1"), "The Divided Dial")
            val ruleB = SeriesRule(PodcastId("podcast-2"), "The Divided Dial")

            persistence.add(ruleA)
            persistence.add(ruleB)

            persistence.findAll() shouldContainExactlyInAnyOrder listOf(ruleA, ruleB)
        }
    }

    describe("remove") {
        it("deletes the rule and returns true") {
            val persistence = persistenceProvider()
            val rule = SeriesRule(PodcastId("podcast-1"), "The Divided Dial")
            persistence.add(rule)

            val result = persistence.remove(rule)

            result shouldBe true
            persistence.findAll() shouldBe emptyList()
        }

        it("returns false for an unknown rule and leaves others intact") {
            val persistence = persistenceProvider()
            val rule = SeriesRule(PodcastId("podcast-1"), "The Divided Dial")
            persistence.add(rule)

            val result = persistence.remove(SeriesRule(PodcastId("podcast-1"), "Unknown Series"))

            result shouldBe false
            persistence.findAll() shouldBe listOf(rule)
        }
    }

    describe("removeAllFor") {
        it("deletes every rule belonging to the podcast") {
            val persistence = persistenceProvider()
            persistence.add(SeriesRule(PodcastId("podcast-1"), "The Divided Dial"))
            persistence.add(SeriesRule(PodcastId("podcast-1"), "Serial"))

            persistence.removeAllFor(PodcastId("podcast-1"))

            persistence.findAll() shouldBe emptyList()
        }

        it("leaves rules belonging to other podcasts intact") {
            val persistence = persistenceProvider()
            val other = SeriesRule(PodcastId("podcast-2"), "The Divided Dial")
            persistence.add(SeriesRule(PodcastId("podcast-1"), "The Divided Dial"))
            persistence.add(other)

            persistence.removeAllFor(PodcastId("podcast-1"))

            persistence.findAll() shouldBe listOf(other)
        }

        it("does nothing for a podcast with no rules") {
            val persistence = persistenceProvider()
            val rule = SeriesRule(PodcastId("podcast-1"), "The Divided Dial")
            persistence.add(rule)

            persistence.removeAllFor(PodcastId("podcast-2"))

            persistence.findAll() shouldBe listOf(rule)
        }
    }
}
