package podcast.core.ports

import io.kotest.core.factory.TestFactory
import io.kotest.core.spec.style.describeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import shared.model.EpisodeId
import java.time.Instant
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

fun podcastCatalogContract(catalogProvider: () -> PodcastCatalog): TestFactory = describeSpec {

    describe("save") {
        it("persists a podcast with its episodes") {
            val catalog = catalogProvider()
            val podcastId = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(podcastId.value), listOf(
                createEpisode("e1", podcastId.value),
                createEpisode("e2", podcastId.value)
            ))

            catalog.findById(podcastId) shouldNotBe null
            catalog.episodesFor(podcastId) shouldHaveSize 2
        }

        it("persists a podcast with no episodes") {
            val catalog = catalogProvider()
            val podcastId = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(podcastId.value), emptyList())

            catalog.findById(podcastId) shouldNotBe null
            catalog.episodesFor(podcastId).shouldBeEmpty()
        }

        it("updates podcast fields on re-save with the same id") {
            val catalog = catalogProvider()
            val id = PodcastId("same-id")
            catalog.save(createPodcast(id.value).copy(name = "Old Name"), emptyList())
            catalog.save(createPodcast(id.value).copy(name = "New Name"), emptyList())

            catalog.findById(id)?.name shouldBe "New Name"
        }

        it("preserves listening status when an existing podcast is re-saved") {
            val catalog = catalogProvider()
            val id = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(id.value), emptyList())
            catalog.setListening(id, false)

            catalog.save(createPodcast(id.value), emptyList())

            catalog.findById(id)?.listening shouldBe false
        }

        it("dedups episodes on feed guid, keeping the original episode id") {
            val catalog = catalogProvider()
            val podcastId = PodcastId(UUID.randomUUID().toString())
            val original = createEpisode("e-original", podcastId.value).copy(feedGuid = "guid-1", title = "Old Title")
            val refetched = createEpisode("e-refetched", podcastId.value).copy(feedGuid = "guid-1", title = "New Title")

            catalog.save(createPodcast(podcastId.value), listOf(original))
            catalog.save(createPodcast(podcastId.value), listOf(refetched))

            val episodes = catalog.episodesFor(podcastId)
            episodes shouldHaveSize 1
            episodes.first().id shouldBe EpisodeId("e-original")
            episodes.first().title shouldBe "New Title"
        }
    }

    describe("delete") {
        it("removes the podcast and its episodes") {
            val catalog = catalogProvider()
            val id = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(id.value), listOf(
                createEpisode("e1", id.value),
                createEpisode("e2", id.value)
            ))

            catalog.delete(id)

            catalog.findById(id) shouldBe null
            catalog.episodesFor(id).shouldBeEmpty()
        }

        it("does not affect other podcasts") {
            val catalog = catalogProvider()
            val keep = PodcastId(UUID.randomUUID().toString())
            val remove = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(keep.value), listOf(createEpisode("k1", keep.value)))
            catalog.save(createPodcast(remove.value), listOf(createEpisode("r1", remove.value)))

            catalog.delete(remove)

            catalog.findById(keep) shouldNotBe null
            catalog.episodesFor(keep) shouldHaveSize 1
        }

        it("is a no-op for a missing podcast") {
            val catalog = catalogProvider()
            catalog.delete(PodcastId("non-existent"))
            catalog.findAll().shouldBeEmpty()
        }
    }

    describe("findAll") {
        it("returns all added podcasts") {
            val catalog = catalogProvider()
            catalog.findAll().shouldBeEmpty()
            catalog.save(createPodcast("1"), emptyList())
            catalog.save(createPodcast("2"), emptyList())
            catalog.findAll() shouldHaveSize 2
        }
    }

    describe("findById") {
        it("returns null for a missing podcast") {
            val catalog = catalogProvider()
            catalog.findById(PodcastId("non-existent")) shouldBe null
        }
    }

    describe("findByUrl") {
        it("returns the podcast with the given feed url") {
            val catalog = catalogProvider()
            catalog.save(createPodcast("a"), emptyList())
            catalog.save(createPodcast("b"), emptyList())

            catalog.findByUrl(FeedUrl("url-b"))?.id shouldBe PodcastId("b")
        }

        it("returns null for an unknown url") {
            val catalog = catalogProvider()
            catalog.save(createPodcast("a"), emptyList())

            catalog.findByUrl(FeedUrl("nope")) shouldBe null
        }
    }

    describe("episodesFor") {
        it("does not return episodes belonging to a different podcast") {
            val catalog = catalogProvider()
            val id1 = PodcastId(UUID.randomUUID().toString())
            val id2 = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(id1.value), listOf(createEpisode("e1", id1.value)))
            catalog.save(createPodcast(id2.value), emptyList())

            catalog.episodesFor(id2).shouldBeEmpty()
        }
    }

    describe("findEpisodeById") {
        it("returns the episode with the given id") {
            val catalog = catalogProvider()
            val podcastId = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(podcastId.value), listOf(createEpisode("e1", podcastId.value)))

            catalog.findEpisodeById(EpisodeId("e1"))?.title shouldBe "Episode e1"
        }

        it("returns null for a missing episode") {
            val catalog = catalogProvider()
            catalog.findEpisodeById(EpisodeId("non-existent")) shouldBe null
        }
    }

    describe("findEpisodesPublishedAfter") {
        it("returns only episodes published strictly after the given instant") {
            val catalog = catalogProvider()
            val podcastId = PodcastId(UUID.randomUUID().toString())
            val cutoff = Instant.parse("2026-02-01T00:00:00Z")
            catalog.save(createPodcast(podcastId.value), listOf(
                createEpisode("e-at-cutoff", podcastId.value).copy(publishedAt = cutoff),
                createEpisode("e-after", podcastId.value).copy(publishedAt = cutoff.plusSeconds(60)),
                createEpisode("e-before", podcastId.value).copy(publishedAt = cutoff.minusSeconds(60)),
                createEpisode("e-undated", podcastId.value).copy(publishedAt = null),
            ))

            val result = catalog.findEpisodesPublishedAfter(cutoff)
            result.map { it.id } shouldContainExactlyInAnyOrder listOf(EpisodeId("e-after"))
        }
    }

    describe("latestEpisodeAt") {
        it("is the most recent episode publish date") {
            val catalog = catalogProvider()
            val podcastId = PodcastId(UUID.randomUUID().toString())
            val newest = Instant.parse("2026-03-01T00:00:00Z")
            catalog.save(createPodcast(podcastId.value), listOf(
                createEpisode("e-older", podcastId.value).copy(publishedAt = Instant.parse("2026-02-01T00:00:00Z")),
                createEpisode("e-newest", podcastId.value).copy(publishedAt = newest),
            ))

            catalog.findById(podcastId)?.latestEpisodeAt shouldBe newest
        }

        it("falls back to created when there are no episodes") {
            val catalog = catalogProvider()
            val podcastId = PodcastId(UUID.randomUUID().toString())
            val created = Instant.parse("2026-01-01T00:00:00Z")
            catalog.save(createPodcast(podcastId.value).copy(created = created), emptyList())

            catalog.findById(podcastId)?.latestEpisodeAt shouldBe created
        }

        it("falls back to created when no episode has a publish date") {
            val catalog = catalogProvider()
            val podcastId = PodcastId(UUID.randomUUID().toString())
            val created = Instant.parse("2026-01-01T00:00:00Z")
            catalog.save(
                createPodcast(podcastId.value).copy(created = created),
                listOf(createEpisode("e-undated", podcastId.value).copy(publishedAt = null)),
            )

            catalog.findById(podcastId)?.latestEpisodeAt shouldBe created
        }
    }

    describe("listening") {
        it("new podcast has listening = true by default") {
            val catalog = catalogProvider()
            val id = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(id.value), emptyList())

            catalog.findById(id)?.listening shouldBe true
        }

        it("findAll returns listening podcasts before non-listening") {
            val catalog = catalogProvider()
            val listening = PodcastId(UUID.randomUUID().toString())
            val notListening = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(listening.value), emptyList())
            catalog.save(createPodcast(notListening.value), emptyList())
            catalog.setListening(notListening, false)

            val all = catalog.findAll()
            all.first().id shouldBe listening
            all.last().id shouldBe notListening
        }

        it("setListening returns true when podcast exists") {
            val catalog = catalogProvider()
            val id = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(id.value), emptyList())

            catalog.setListening(id, false) shouldBe true
            catalog.findById(id)?.listening shouldBe false

            catalog.setListening(id, true) shouldBe true
            catalog.findById(id)?.listening shouldBe true
        }

        it("setListening returns false for unknown podcast") {
            val catalog = catalogProvider()
            catalog.setListening(PodcastId("nope"), false) shouldBe false
        }
    }
}

private fun createPodcast(id: String) = Podcast(
    id = PodcastId(id),
    url = FeedUrl("url-$id"),
    name = "Name $id",
    image = "img",
    listening = true,
    created = Instant.now(),
    updated = Instant.now(),
    latestEpisodeAt = Instant.now(),
)

private fun createEpisode(id: String, podcastId: String) = Episode(
    id = EpisodeId(id),
    feedGuid = id,
    podcastId = PodcastId(podcastId),
    title = "Episode $id",
    description = "Desc",
    audioUrl = "https://audio/$id.mp3",
    duration = 1.minutes,
    publishedAt = Instant.now()
)
