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
import podcast.core.model.EpisodeId
import podcast.core.model.FeedUrl
import podcast.core.model.Podcast
import podcast.core.model.PodcastId
import java.sql.DriverManager
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.minutes

class SQLitePersistenceIT : DescribeSpec({

    lateinit var db: SingleConnectionProvider
    lateinit var catalog: SQLitePodcastCatalog
    lateinit var podcasts: SQLitePodcastPersistence
    lateinit var episodes: SQLiteEpisodePersistence

    beforeEach {
        db = SingleConnectionProvider(DriverManager.getConnection("jdbc:sqlite::memory:"))
        catalog = SQLitePodcastCatalog(db)
        podcasts = SQLitePodcastPersistence(db)
        episodes = SQLiteEpisodePersistence(db)

        db.withConnection { conn ->
            conn.createStatement().use { it.execute(CREATE_PODCASTS_TABLE) }
            conn.createStatement().use { it.execute(CREATE_EPISODES_TABLE) }
        }
    }

    afterEach {
        db.close()
    }

    describe("SQLitePodcastCatalog") {
        it("should persist a podcast and its episodes atomically") {
            val podcastId = PodcastId(UUID.randomUUID().toString())
            catalog.register(createPodcast(podcastId.value), listOf(
                createEpisode("e1", podcastId.value),
                createEpisode("e2", podcastId.value)
            ))

            podcasts.findById(podcastId) shouldNotBe null
            episodes.findByPodcastId(podcastId) shouldHaveSize 2
        }

        it("should persist a podcast with no episodes") {
            val podcastId = PodcastId(UUID.randomUUID().toString())
            catalog.register(createPodcast(podcastId.value), emptyList())

            podcasts.findById(podcastId) shouldNotBe null
            episodes.findByPodcastId(podcastId).shouldBeEmpty()
        }

        it("should upsert on podcast id conflict") {
            val id = PodcastId("same-id")
            catalog.register(Podcast(id, FeedUrl("url"), "Old Name", "img", Instant.now()), emptyList())
            catalog.register(Podcast(id, FeedUrl("url"), "New Name", "img", Instant.now()), emptyList())

            podcasts.findById(id)?.name shouldBe "New Name"
        }
    }

    describe("SQLitePodcastPersistence") {
        it("should return null for a missing podcast") {
            podcasts.findById(PodcastId("non-existent")) shouldBe null
        }

        it("should return all saved podcasts") {
            podcasts.findAll().shouldBeEmpty()

            catalog.register(createPodcast("1"), emptyList())
            catalog.register(createPodcast("2"), emptyList())

            podcasts.findAll() shouldHaveSize 2
        }
    }

    describe("SQLiteEpisodePersistence") {
        it("should not return episodes for a different podcast") {
            val id1 = PodcastId(UUID.randomUUID().toString())
            val id2 = PodcastId(UUID.randomUUID().toString())
            catalog.register(createPodcast(id1.value), listOf(createEpisode("e1", id1.value)))
            catalog.register(createPodcast(id2.value), emptyList())

            episodes.findByPodcastId(id2).shouldBeEmpty()
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
