package podcast

import installCommon
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import podcast.infrastructure.web.AddPodcastRequest
import podcast.infrastructure.web.PodcastDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PodcastApiTest : StringSpec({
    val fixedInstant = Instant.parse("2026-04-10T10:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    "should save and retrieve podcasts through the full stack" {
        val feed = "https://example.com/feed.xml"

        val response =
            """<rss xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
                    <channel>
                        <title>The AI Show</title>
                        <itunes:image href="https://example.com/itunes-image.png"/>
                    </channel>
                </rss>""".trimIndent()

        val testHttpClient = HttpClient(configureMockEngine(feed to response))

        testApplication {
            podcastModuleConfiguration(testHttpClient, fixedClock)
            val client = createDefaultClient()

            client.post("/podcasts") {
                contentType(ContentType.Application.Json)
                setBody(AddPodcastRequest(feed))
            }.status shouldBe HttpStatusCode.OK

            val podcasts = client.get("/podcasts").body<List<PodcastDto>>()

            podcasts shouldHaveSize 1
            with(podcasts.first()) {
                url shouldBe feed
                name shouldBe "The AI Show"
                image shouldBe "https://example.com/itunes-image.png"
                createdAt shouldBe fixedInstant.toString()
            }
        }
    }

    "should not create duplicate podcasts for the same feed" {
        val feed = "https://example.com/feed.xml"
        val response = """<rss><channel><title>This is a mock</title></channel></rss>"""
        val testHttpClient = HttpClient(configureMockEngine(feed to response))

        testApplication {
            podcastModuleConfiguration(testHttpClient, fixedClock)
            val client = createDefaultClient()

            repeat(2) {
                client.post("/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feed))
                }.status shouldBe HttpStatusCode.OK
            }

            val podcasts = client.get("/podcasts").body<List<PodcastDto>>()
            podcasts shouldHaveSize 1
            podcasts.first().url shouldBe feed
        }
    }

    "should list all saved podcasts" {
        val feed1 = "https://example.com/feed1.xml"
        val feed2 = "https://example.com/feed2.xml"
        val response = """<rss><channel><title>{podcast-title}</title></channel></rss>"""

        val testHttpClient = HttpClient(
            configureMockEngine(feed1 to response.replace("{podcast-title}", feed1), feed2 to response.replace("{podcast-title}", feed2))
        )

        testApplication {
            podcastModuleConfiguration(testHttpClient, fixedClock)
            val client = createDefaultClient()

            client.post("/podcasts") {
                contentType(ContentType.Application.Json)
                setBody(AddPodcastRequest(feed1))
            }.status shouldBe HttpStatusCode.OK

            client.post("/podcasts") {
                contentType(ContentType.Application.Json)
                setBody(AddPodcastRequest(feed2))
            }.status shouldBe HttpStatusCode.OK

            val podcasts = client.get("/podcasts").body<List<PodcastDto>>()
            podcasts shouldHaveSize 2
            podcasts.map { it.url } shouldBe listOf(feed1, feed2)
        }
    }
})

private fun ApplicationTestBuilder.createDefaultClient(): HttpClient =
    createClient {
        install(ContentNegotiation) { json() }
    }

private fun ApplicationTestBuilder.podcastModuleConfiguration(testHttpClient: HttpClient, fixedClock: Clock) {
    application {
        installCommon(httpClient = testHttpClient)
        installPodcastModule(clock = fixedClock)
    }
}

private fun configureMockEngine(vararg mappings: Pair<String, String>): MockEngine =
    mappings.toMap()
        .let { responses ->
            MockEngine { request ->
                responses[request.url.toString()]
                    ?.let {
                        respond(
                            content = it,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/xml")
                        )
                    }
                    ?: respondError(HttpStatusCode.NotFound)
            }
        }
