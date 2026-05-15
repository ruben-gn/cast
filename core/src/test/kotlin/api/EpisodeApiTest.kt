package api

import application.installApplicationModule
import installCommon
import installHttpClient
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import cast.api.AddPodcastRequest
import cast.api.PodcastDetailDto
import playback.fakes.FakePlaybackPersistence
import playback.installPlaybackModule
import settings.fakes.FakeSettingsPersistence
import settings.installSettingsModule
import podcast.fakes.FakePodcastCatalog
import podcast.installPodcastModule
import shared.model.EpisodeId
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class EpisodeApiTest : DescribeSpec({
    val fixedClock = Clock.fixed(Instant.parse("2026-04-10T10:00:00Z"), ZoneId.of("UTC"))
    val feed = "https://example.com/feed.xml"
    val rss = """
        <rss><channel>
            <title>Test Show</title>
            <image><url>https://example.com/img.png</url></image>
            <item><title>Episode 1</title><enclosure url="https://cdn/ep1.mp3" length="0" type="audio/mpeg"/></item>
        </channel></rss>
    """.trimIndent()

    fun testApp(
        playback: FakePlaybackPersistence = FakePlaybackPersistence(),
        block: suspend ApplicationTestBuilder.(client: io.ktor.client.HttpClient) -> Unit,
    ) {
        testApplication {
            application {
                installHttpClient(HttpClient(MockEngine { respond(rss, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/xml")) }))
                installCommon(clock = fixedClock)
                installPodcastModule(podcastCatalog = FakePodcastCatalog())
                installPlaybackModule(playbackState = playback)
                installSettingsModule(FakeSettingsPersistence())
                installApplicationModule()
                routing {
                    route("/api/podcasts") { podcastApi(dependencies) }
                    route("/api/episodes") { episodeApi(dependencies) }
                }
            }
            block(createClient { install(ContentNegotiation) { json() } })
        }
    }

    describe("POST /api/episodes/{id}/played") {
        it("marks the episode as played and returns 204") {
            val playback = FakePlaybackPersistence()
            testApp(playback = playback) { client ->
                val podcast = client.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feed))
                }.body<PodcastDetailDto>()
                val episodeId = podcast.episodes.first().id

                client.post("/api/episodes/${episodeId.encodeURLPathPart()}/played").status shouldBe HttpStatusCode.NoContent

                playback.get(EpisodeId(episodeId))!!.played shouldBe true
            }
        }

        it("returns 404 for an unknown episode") {
            testApp { client ->
                client.post("/api/episodes/nonexistent/played").status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
