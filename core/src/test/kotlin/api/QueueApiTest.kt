package api

import application.installApplicationModule
import installCommon
import installHttpClient
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import cast.api.EpisodeDetailDto
import playback.fakes.FakePlaybackPersistence
import playback.installPlaybackModule
import podcast.core.models.Episode
import podcast.core.models.PodcastId
import podcast.fakes.FakePodcastCatalog
import podcast.installPodcastModule
import queue.fakes.FakeQueuePersistence
import queue.installQueueModule
import shared.model.EpisodeId
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class QueueApiTest : DescribeSpec({
    val fixedClock = Clock.fixed(Instant.parse("2026-04-10T10:00:00Z"), ZoneId.of("UTC"))

    fun testSetup(
        catalog: FakePodcastCatalog = FakePodcastCatalog(),
        queuePersistence: FakeQueuePersistence = FakeQueuePersistence(),
    ) = TestSetup(catalog, queuePersistence)

    describe("Queue API") {

        it("GET /api/queue returns an empty list when queue is empty") {
            val setup = testSetup()
            testApplication {
                application { setup.install(this, fixedClock) }
                val client = createClient { install(ContentNegotiation) { json() } }

                val episodes = client.get("/api/queue").body<List<EpisodeDetailDto>>()
                episodes.shouldBeEmpty()
            }
        }

        it("POST /api/queue/{id} adds an episode to the queue and returns updated list") {
            val catalog = FakePodcastCatalog()
            val episode = anEpisode(catalog)
            val setup = testSetup(catalog = catalog)

            testApplication {
                application { setup.install(this, fixedClock) }
                val client = createClient { install(ContentNegotiation) { json() } }

                val result = client.post("/api/queue/${episode.id.value}").body<List<EpisodeDetailDto>>()
                result shouldHaveSize 1
                result.first().id shouldBe episode.id.value
                result.first().title shouldBe episode.title
            }
        }

        it("DELETE /api/queue/{id} removes an episode and returns updated list") {
            val catalog = FakePodcastCatalog()
            val ep1 = anEpisode(catalog, id = "ep-1", title = "First")
            val ep2 = anEpisode(catalog, id = "ep-2", title = "Second")
            val queue = FakeQueuePersistence().also {
                it.save(queue.core.model.Queue(listOf(EpisodeId("ep-1"), EpisodeId("ep-2"))))
            }
            val setup = testSetup(catalog = catalog, queuePersistence = queue)

            testApplication {
                application { setup.install(this, fixedClock) }
                val client = createClient { install(ContentNegotiation) { json() } }

                val result = client.delete("/api/queue/${ep1.id.value}").body<List<EpisodeDetailDto>>()
                result shouldHaveSize 1
                result.first().id shouldBe ep2.id.value
            }
        }

        it("maintains queue order across add and remove operations") {
            val catalog = FakePodcastCatalog()
            val ep1 = anEpisode(catalog, id = "ep-1", title = "First")
            val ep2 = anEpisode(catalog, id = "ep-2", title = "Second")
            val ep3 = anEpisode(catalog, id = "ep-3", title = "Third")
            val setup = testSetup(catalog = catalog)

            testApplication {
                application { setup.install(this, fixedClock) }
                val client = createClient { install(ContentNegotiation) { json() } }

                client.post("/api/queue/${ep1.id.value}")
                client.post("/api/queue/${ep2.id.value}")
                client.post("/api/queue/${ep3.id.value}")

                val result = client.get("/api/queue").body<List<EpisodeDetailDto>>()
                result.map { it.id } shouldBe listOf("ep-1", "ep-2", "ep-3")
            }
        }
    }
})

private suspend fun anEpisode(
    catalog: FakePodcastCatalog,
    id: String = "ep-${System.nanoTime()}",
    title: String = "Episode $id",
): Episode {
    val podcastId = PodcastId("podcast-1")
    val episode = Episode(
        id = EpisodeId(id),
        podcastId = podcastId,
        title = title,
        description = "",
        audioUrl = "https://example.com/$id.mp3",
        duration = null,
        publishedAt = null,
    )
    catalog.save(
        podcast.core.models.Podcast(
            id = podcastId,
            url = podcast.core.models.FeedUrl("https://example.com/feed.xml"),
            name = "Test Podcast",
            image = "https://example.com/image.png",
            created = java.time.Instant.EPOCH,
            updated = java.time.Instant.EPOCH,
        ),
        listOf(episode),
    )
    return episode
}

private class TestSetup(
    private val catalog: FakePodcastCatalog,
    private val queuePersistence: FakeQueuePersistence,
) {
    fun install(app: io.ktor.server.application.Application, clock: Clock) = with(app) {
        installHttpClient()
        installCommon(clock = clock)
        installPodcastModule(podcastCatalog = catalog)
        installPlaybackModule(playbackState = FakePlaybackPersistence())
        installQueueModule(queuePersistence = queuePersistence)
        installApplicationModule()
        routing { route("/api/queue") { queueApi(dependencies) } }
    }
}
