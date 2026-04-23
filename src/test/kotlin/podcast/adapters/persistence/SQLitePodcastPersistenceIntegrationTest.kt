package podcast.adapters.persistence

import configuration.CREATE_EPISODES_TABLE
import configuration.CREATE_PODCASTS_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import podcast.core.model.Episode
import shared.model.EpisodeId
import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId
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
            catalog.add(createPodcast(podcastId.value), listOf(
                createEpisode("e1", podcastId.value),
                createEpisode("e2", podcastId.value)
            ))

            catalog.findById(podcastId) shouldNotBe null
            catalog.episodesFor(podcastId) shouldHaveSize 2
        }

        it("should persist a podcast with no episodes") {
            val podcastId = PodcastId(UUID.randomUUID().toString())
            catalog.add(createPodcast(podcastId.value), emptyList())

            catalog.findById(podcastId) shouldNotBe null
            catalog.episodesFor(podcastId).shouldBeEmpty()
        }

        it("should upsert on podcast id conflict") {
            val id = PodcastId("same-id")
            catalog.add(Podcast(id, FeedUrl("url"), "Old Name", "img", Instant.now()), emptyList())
            catalog.add(Podcast(id, FeedUrl("url"), "New Name", "img", Instant.now()), emptyList())

            catalog.findById(id)?.name shouldBe "New Name"
        }
    }

    describe("findAll") {
        it("should return all added podcasts") {
            catalog.findAll().shouldBeEmpty()
            catalog.add(createPodcast("1"), emptyList())
            catalog.add(createPodcast("2"), emptyList())
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
            catalog.add(createPodcast(id1.value), listOf(createEpisode("e1", id1.value)))
            catalog.add(createPodcast(id2.value), emptyList())

            catalog.episodesFor(id2).shouldBeEmpty()
        }
    }
})

private fun createPodcast(id: String) = Podcast(
    id = PodcastId(id),
    url = FeedUrl("url-$id"),
    name = "Name $id",
    image = "img",
    createdAt = Instant.now()
)

private fun createEpisode(id: String, podcastId: String) = Episode(
    id = EpisodeId(id),
    podcastId = PodcastId(podcastId),
    title = "Episode $id",
    description = "Desc",
    audioUrl = "https://audio/$id.mp3",
    duration = 1.minutes,
    publishedAt = Instant.now()
)
