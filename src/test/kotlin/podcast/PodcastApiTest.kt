package podcast

import installCommon
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
import io.ktor.server.testing.*
import podcast.adapters.web.AddPodcastRequest
import podcast.adapters.web.PodcastSummaryDto
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class PodcastApiTest : DescribeSpec({
    val fixedInstant = Instant.parse("2026-04-10T10:00:00Z")
    val fixedClock = Clock.fixed(fixedInstant, ZoneId.of("UTC"))

    describe("Podcast API") {
        it("should support the full lifecycle of podcast registration and listing") {
            val feed = "https://example.com/feed.xml"
            val rssResponse = """
                <rss><channel>
                    <title>The AI Show</title>
                    <image><url>https://example.com/image.png</url></image>
                </channel></rss>
            """.trimIndent()

            val feed2 = "https://example.com/feed2.xml"
            val rssResponse2 = """
                <rss><channel>
                    <title>The AI Show 2</title>
                    <image><url>https://example.com/image2.png</url></image>
                </channel></rss>
            """.trimIndent()

            val testHttpClient = HttpClient(
                configureMockEngine(
                    feed to rssResponse,
                    feed2 to rssResponse2
                )
            )

            testApplication {
                application {
                    installCommon(httpClient = testHttpClient)
                    installPodcastModule(clock = fixedClock)
                }
                val client = createClient {
                    this.install(ContentNegotiation) { json() }
                }

                val response1 = client.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feed))
                }
                response1.status shouldBe HttpStatusCode.OK

                val response2 = client.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feed2))
                }
                response2.status shouldBe HttpStatusCode.OK

                val podcasts = client.get("/api/podcasts").body<List<PodcastSummaryDto>>()

                podcasts shouldHaveSize 2
                podcasts.map { it.url } shouldBe listOf(feed, feed2)
                podcasts.map { it.name } shouldBe listOf("The AI Show", "The AI Show 2")
                podcasts.map { it.image } shouldBe listOf("https://example.com/image.png", "https://example.com/image2.png")
            }
        }
    }
})

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
