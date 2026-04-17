package podcast.adapters.persistence

import configuration.CREATE_EPISODES_TABLE
import configuration.CREATE_PODCASTS_TABLE
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import podcast.core.model.Episode
import podcast.core.model.Podcast
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.*

class SQLitePodcastPersistenceIT : DescribeSpec({

    lateinit var connection: Connection
    lateinit var persistence: SQLitePodcastPersistence

    beforeEach {
        connection = DriverManager.getConnection("jdbc:sqlite::memory:")
        persistence = SQLitePodcastPersistence(connection)

        // Initialize schema
        connection.createStatement().use { it.execute(CREATE_PODCASTS_TABLE) }
        connection.createStatement().use { it.execute(CREATE_EPISODES_TABLE) }
    }

    afterEach {
        connection.close()
    }

    describe("save") {
        it("should persist a podcast and its episodes") {
            val podcastId = UUID.randomUUID().toString()
            val podcast = Podcast(
                id = podcastId,
                url = "https://example.com/rss",
                name = "Kotlin Weekly",
                image = "https://example.com/img.png",
                createdAt = Instant.parse("2026-04-17T10:00:00Z"),
                episodes = listOf(
                    Episode(UUID.randomUUID().toString(), "Ep 1", "Desc", "https://audio.mp3", "01:00", Instant.now())
                )
            )

            persistence.save(podcast)

            val found = persistence.findById(podcastId)!!

            found.name shouldBe "Kotlin Weekly"
            found.episodes shouldHaveSize 1
        }

        it("should update an existing podcast (upsert logic)") {
            val id = "same-id"
            val initial = Podcast(id, "url", "Old Name", "img", Instant.now(), emptyList())
            val updated = Podcast(id, "url", "New Name", "img", Instant.now(), emptyList())

            persistence.save(initial)
            persistence.save(updated)

            persistence.findById(id)?.name shouldBe "New Name"
        }
    }

    describe("findById") {
        it("should return null if podcast does not exist") {
            persistence.findById("non-existent") shouldBe null
        }
    }

    describe("findAll") {
        it("should return all saved podcasts") {
            persistence.findAll().shouldBeEmpty()

            persistence.save(createPodcast("1"))
            persistence.save(createPodcast("2"))

            persistence.findAll() shouldHaveSize 2
        }
    }
})

private fun createPodcast(id: String) = Podcast(
    id = id,
    url = "url-$id",
    name = "Name $id",
    image = "img",
    createdAt = Instant.now(),
    episodes = emptyList()
)