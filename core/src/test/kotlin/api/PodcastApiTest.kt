package api

import application.installApplicationModule
import installCommon
import installHttpClient
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
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
import cast.api.PodcastSummaryDto
import playback.installPlaybackModule
import playback.fakes.FakePlaybackPersistence
import settings.installSettingsModule
import podcast.fakes.FakePodcastCatalog
import podcast.installPodcastModule
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PodcastApiTest : DescribeSpec({
    val fixedClock = Clock.fixed(Instant.parse("2026-04-10T10:00:00Z"), ZoneId.of("UTC"))

    fun testApp(vararg feeds: Pair<String, String>, block: suspend ApplicationTestBuilder.(client: io.ktor.client.HttpClient) -> Unit) {
        testApplication {
            application {
                installHttpClient(HttpClient(configureMockEngine(*feeds)))
                installCommon(clock = fixedClock)
                installPodcastModule(podcastCatalog = FakePodcastCatalog())
                installPlaybackModule(playbackState = FakePlaybackPersistence())
                installSettingsModule()
                installApplicationModule()
                routing { route("/api/podcasts") { podcastApi(dependencies) } }
            }
            block(createClient { install(ContentNegotiation) { json() } })
        }
    }

    describe("Podcast API") {
        it("should support the full lifecycle of podcast registration and listing") {
            val feed = "https://example.com/feed.xml"
            val feed2 = "https://example.com/feed2.xml"
            testApp(
                feed to "<rss><channel><title>The AI Show</title><image><url>https://example.com/image.png</url></image></channel></rss>",
                feed2 to "<rss><channel><title>The AI Show 2</title><image><url>https://example.com/image2.png</url></image></channel></rss>",
            ) { client ->
                client.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feed))
                }.status shouldBe HttpStatusCode.OK

                client.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feed2))
                }.status shouldBe HttpStatusCode.OK

                val podcasts = client.get("/api/podcasts").body<List<PodcastSummaryDto>>()

                podcasts shouldHaveSize 2
                podcasts.map { it.url } shouldBe listOf(feed, feed2)
                podcasts.map { it.name } shouldBe listOf("The AI Show", "The AI Show 2")
                podcasts.map { it.image } shouldBe listOf("https://example.com/image.png", "https://example.com/image2.png")
            }
        }
    }

    describe("POST /{id}/played") {
        it("returns 204 for a known podcast") {
            val feed = "https://example.com/feed.xml"
            val rss = """
                <rss><channel>
                    <title>Test Show</title>
                    <image><url>https://example.com/img.png</url></image>
                    <item><title>Episode 1</title><enclosure url="https://cdn/ep1.mp3" length="0" type="audio/mpeg"/></item>
                </channel></rss>
            """.trimIndent()
            testApp(feed to rss) { client ->
                val podcast = client.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feed))
                }.body<PodcastDetailDto>()

                client.post("/api/podcasts/${podcast.id}/played").status shouldBe HttpStatusCode.NoContent
            }
        }

        it("returns 404 for an unknown podcast") {
            testApp { client ->
                client.post("/api/podcasts/unknown-id/played").status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})

private fun configureMockEngine(vararg mappings: Pair<String, String>): MockEngine =
    mappings.toMap().let { responses ->
        MockEngine { request ->
            responses[request.url.toString()]
                ?.let { respond(it, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/xml")) }
                ?: respondError(HttpStatusCode.NotFound)
        }
    }
