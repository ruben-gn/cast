package podcast.adapters.persistence

import configuration.CREATE_EPISODES_TABLE
import configuration.CREATE_PODCASTS_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import podcast.core.models.Episode
import shared.model.EpisodeId
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import java.sql.DriverManager
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.minutes

class SQLitePodcastCatalogIT : DescribeSpec({

    lateinit var db: SingleConnectionProvider
    lateinit var catalog: SQLitePodcastCatalog

    beforeEach {
        db = SingleConnectionProvider(DriverManager.getConnection("jdbc:sqlite::memory:"))
        catalog = SQLitePodcastCatalog(db)
        db.withConnection { conn ->
            conn.createStatement().use { it.execute(CREATE_PODCASTS_TABLE) }
            conn.createStatement().use { it.execute(CREATE_EPISODES_TABLE) }
        }
    }

    afterEach { db.close() }

    describe("add") {
        it("should persist a podcast with its episodes atomically") {
            val podcastId = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(podcastId.value), listOf(
                createEpisode("e1", podcastId.value),
                createEpisode("e2", podcastId.value)
            ))

            catalog.findById(podcastId) shouldNotBe null
            catalog.episodesFor(podcastId) shouldHaveSize 2
        }

        it("should persist a podcast with no episodes") {
            val podcastId = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(podcastId.value), emptyList())

            catalog.findById(podcastId) shouldNotBe null
            catalog.episodesFor(podcastId).shouldBeEmpty()
        }

        it("should upsert on podcast id conflict") {
            val id = PodcastId("same-id")
            catalog.save(Podcast(id, FeedUrl("url"), "Old Name", "img", true, Instant.now(), Instant.now(), Instant.now()), emptyList())
            catalog.save(Podcast(id, FeedUrl("url"), "New Name", "img", true, Instant.now(), Instant.now(), Instant.now()), emptyList())

            catalog.findById(id)?.name shouldBe "New Name"
        }
    }

    describe("delete") {
        it("should remove the podcast and its episodes") {
            val id = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(id.value), listOf(
                createEpisode("e1", id.value),
                createEpisode("e2", id.value)
            ))

            catalog.delete(id)

            catalog.findById(id) shouldBe null
            catalog.episodesFor(id).shouldBeEmpty()
        }

        it("should not affect other podcasts") {
            val keep = PodcastId(UUID.randomUUID().toString())
            val remove = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(keep.value), listOf(createEpisode("k1", keep.value)))
            catalog.save(createPodcast(remove.value), listOf(createEpisode("r1", remove.value)))

            catalog.delete(remove)

            catalog.findById(keep) shouldNotBe null
            catalog.episodesFor(keep) shouldHaveSize 1
        }

        it("should be a no-op for a missing podcast") {
            catalog.delete(PodcastId("non-existent"))
            catalog.findAll().shouldBeEmpty()
        }
    }

    describe("findAll") {
        it("should return all added podcasts") {
            catalog.findAll().shouldBeEmpty()
            catalog.save(createPodcast("1"), emptyList())
            catalog.save(createPodcast("2"), emptyList())
            catalog.findAll() shouldHaveSize 2
        }
    }

    describe("findById") {
        it("should return null for a missing podcast") {
            catalog.findById(PodcastId("non-existent")) shouldBe null
        }
    }

    describe("episodesFor") {
        it("should not return episodes belonging to a different podcast") {
            val id1 = PodcastId(UUID.randomUUID().toString())
            val id2 = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(id1.value), listOf(createEpisode("e1", id1.value)))
            catalog.save(createPodcast(id2.value), emptyList())

            catalog.episodesFor(id2).shouldBeEmpty()
        }
    }

    describe("listening") {
        it("new podcast has listening = true by default") {
            val id = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(id.value), emptyList())

            catalog.findById(id)?.listening shouldBe true
        }

        it("findAll returns listening podcasts before non-listening") {
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
            val id = PodcastId(UUID.randomUUID().toString())
            catalog.save(createPodcast(id.value), emptyList())

            catalog.setListening(id, false) shouldBe true
            catalog.findById(id)?.listening shouldBe false

            catalog.setListening(id, true) shouldBe true
            catalog.findById(id)?.listening shouldBe true
        }

        it("setListening returns false for unknown podcast") {
            catalog.setListening(PodcastId("nope"), false) shouldBe false
        }
    }
})

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
