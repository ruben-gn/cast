package api

import application.installApplicationModule
import installCommon
import installHttpClient
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.*
import playback.fakes.FakePlaybackPersistence
import playback.installPlaybackModule
import podcast.fakes.FakePodcastCatalog
import podcast.installPodcastModule
import settings.installSettingsModule
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class ImportOpmlApiTest : DescribeSpec({
    val fixedClock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneId.of("UTC"))

    val rssTemplate = { url: String -> "<rss><channel><title>Show at $url</title></channel></rss>" }

    describe("POST /api/podcasts/import") {
        it("imports all feeds from a valid OPML file and returns counts") {
            val feed1 = "https://example.com/feed1.xml"
            val feed2 = "https://example.com/feed2.xml"
            val opml = """
                <?xml version="1.0"?>
                <opml version="2.0">
                  <body>
                    <outline type="rss" xmlUrl="$feed1"/>
                    <outline type="rss" xmlUrl="$feed2"/>
                  </body>
                </opml>
            """.trimIndent().toByteArray()

            val mockEngine = MockEngine { request ->
                val url = request.url.toString()
                respond(rssTemplate(url), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/xml"))
            }

            testApplication {
                application {
                    installHttpClient(HttpClient(mockEngine))
                    installCommon(clock = fixedClock)
                    installPodcastModule(podcastCatalog = FakePodcastCatalog())
                    installPlaybackModule(playbackState = FakePlaybackPersistence())
                    installSettingsModule()
                    installApplicationModule()
                    routing { route("/api/podcasts") { podcastApi(dependencies) } }
                }

                val client = createClient {}
                val response = client.post("/api/podcasts/import") {
                    setBody(MultiPartFormDataContent(formData {
                        append("opml", opml, Headers.build {
                            append(HttpHeaders.ContentDisposition, """form-data; name="opml"; filename="subscriptions.opml"""")
                        })
                    }))
                }

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText().let { body ->
                    body.contains("\"imported\":2") shouldBe true
                    body.contains("\"failed\":0") shouldBe true
                }
            }
        }

        it("returns 400 when no file is uploaded") {
            testApplication {
                application {
                    installHttpClient(HttpClient(MockEngine { respondError(HttpStatusCode.NotFound) }))
                    installCommon(clock = fixedClock)
                    installPodcastModule(podcastCatalog = FakePodcastCatalog())
                    installPlaybackModule(playbackState = FakePlaybackPersistence())
                    installSettingsModule()
                    installApplicationModule()
                    routing { route("/api/podcasts") { podcastApi(dependencies) } }
                }

                val client = createClient {}
                val response = client.post("/api/podcasts/import") {
                    setBody(MultiPartFormDataContent(formData {}))
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }
})
