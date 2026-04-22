package podcast.adapters.persistence

import configuration.CREATE_EPISODES_TABLE
import configuration.CREATE_PODCASTS_TABLE
import configuration.DatabaseContext
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import podcast.core.model.Episode
import podcast.core.model.Podcast
import java.sql.DriverManager
import java.time.Instant
import java.util.*

class SQLitePodcastPersistenceIT : DescribeSpec({

    lateinit var db: DatabaseContext
    lateinit var podcasts: SQLitePodcastPersistence
    lateinit var episodes: SQLiteEpisodePersistence

    beforeEach {
        db = DatabaseContext(DriverManager.getConnection("jdbc:sqlite::memory:"))
        podcasts = SQLitePodcastPersistence(db)
        episodes = SQLiteEpisodePersistence(db)

        db.connection.createStatement().use { it.execute(CREATE_PODCASTS_TABLE) }
        db.connection.createStatement().use { it.execute(CREATE_EPISODES_TABLE) }
    }

    afterEach {
        db.connection.close()
    }

    describe("SQLitePodcastPersistence") {
        it("should persist and retrieve a podcast") {
            val podcast = createPodcast("1")
            podcasts.save(podcast)

            val found = podcasts.findById("1")!!
            found.name shouldBe "Name 1"
        }

        it("should update an existing podcast (upsert logic)") {
            val id = "same-id"
            podcasts.save(Podcast(id, "url", "Old Name", "img", Instant.now()))
            podcasts.save(Podcast(id, "url", "New Name", "img", Instant.now()))

            podcasts.findById(id)?.name shouldBe "New Name"
        }

        it("should return null for a missing podcast") {
            podcasts.findById("non-existent") shouldBe null
        }

        it("should return all saved podcasts") {
            podcasts.findAll().shouldBeEmpty()

            podcasts.save(createPodcast("1"))
            podcasts.save(createPodcast("2"))

            podcasts.findAll() shouldHaveSize 2
        }
    }

    describe("SQLiteEpisodePersistence") {
        it("should persist and retrieve episodes by podcast id") {
            val podcastId = UUID.randomUUID().toString()
            podcasts.save(createPodcast(podcastId))

            episodes.saveAll(listOf(
                createEpisode("e1", podcastId),
                createEpisode("e2", podcastId)
            ))

            val found = episodes.findByPodcastId(podcastId)
            found shouldHaveSize 2
            found.map { it.id }.toSet() shouldBe setOf("e1", "e2")
        }

        it("should return empty list when podcast has no episodes") {
            val podcastId = UUID.randomUUID().toString()
            podcasts.save(createPodcast(podcastId))

            episodes.findByPodcastId(podcastId).shouldBeEmpty()
        }

        it("should not return episodes for a different podcast") {
            val id1 = UUID.randomUUID().toString()
            val id2 = UUID.randomUUID().toString()
            podcasts.save(createPodcast(id1))
            podcasts.save(createPodcast(id2))

            episodes.saveAll(listOf(createEpisode("e1", id1)))

            episodes.findByPodcastId(id2).shouldBeEmpty()
        }
    }
})

private fun createPodcast(id: String) = Podcast(
    id = id,
    url = "url-$id",
    name = "Name $id",
    image = "img",
    createdAt = Instant.now()
)

private fun createEpisode(id: String, podcastId: String) = Episode(
    id = id,
    podcastId = podcastId,
    title = "Episode $id",
    description = "Desc",
    audioUrl = "https://audio/$id.mp3",
    duration = "01:00",
    publishedAt = Instant.now()
)
