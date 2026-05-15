import api.installRoutes
import application.installApplicationModule
import cast.api.*
import configuration.*
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*
import playback.installPlaybackModule
import podcast.installPodcastModule
import queue.installQueueModule
import settings.installSettingsModule
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class AppTest : DescribeSpec({
    val fixedClock = Clock.fixed(Instant.parse("2026-04-10T10:00:00Z"), ZoneId.of("UTC"))
    val feedUrl = "https://example.com/feed.xml"
    val rss = """
        <rss><channel>
            <title>Test Show</title>
            <image><url>https://example.com/img.png</url></image>
            <item><title>Episode 1</title><guid>ep-1</guid><enclosure url="https://cdn/ep1.mp3" length="0" type="audio/mpeg"/></item>
            <item><title>Episode 2</title><guid>ep-2</guid><enclosure url="https://cdn/ep2.mp3" length="0" type="audio/mpeg"/></item>
        </channel></rss>
    """.trimIndent()

    fun testApp(
        rssFeeds: Map<String, String> = mapOf(feedUrl to rss),
        block: suspend ApplicationTestBuilder.(json: HttpClient, ws: HttpClient) -> Unit,
    ) = testApplication {
        application {
            installHttpClient(HttpClient(MockEngine { request ->
                rssFeeds[request.url.toString()]
                    ?.let { respond(it, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/xml")) }
                    ?: respondError(HttpStatusCode.NotFound)
            }))
            installInMemoryDatabase()
            installCommon(clock = fixedClock)
            installPodcastModule()
            installPlaybackModule()
            installQueueModule()
            installSettingsModule()
            installApplicationModule()
            installRoutes()
        }
        block(
            createClient { install(ContentNegotiation) { json() } },
            createClient { install(WebSockets.Plugin) },
        )
    }

    describe("Podcast") {
        it("add, list, and get detail with episodes") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()

                podcast.name shouldBe "Test Show"
                podcast.episodes.map { it.title } shouldBe listOf("Episode 1", "Episode 2")

                val list = json.get("/api/podcasts").body<List<PodcastSummaryDto>>()
                list.map { it.url } shouldBe listOf(feedUrl)
            }
        }

        it("POST /{id}/played marks all episodes as played") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()

                json.post("/api/podcasts/${podcast.id}/played").status shouldBe HttpStatusCode.NoContent

                val detail = json.get("/api/podcasts/${podcast.id}").body<PodcastDetailDto>()
                detail.episodes.all { it.played } shouldBe true
            }
        }

        it("POST /{id}/played returns 404 for an unknown podcast") {
            testApp { json, _ ->
                json.post("/api/podcasts/does-not-exist/played").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("Episode") {
        it("POST /{id}/played marks episode as played and persists it") {
            testApp { json, ws ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val episodeId = podcast.episodes.first().id

                json.post("/api/episodes/${episodeId.encodeURLPathPart()}/played").status shouldBe HttpStatusCode.NoContent

                ws.webSocket("/api/playback") {
                    send("""{"type":"get","episodeId":"$episodeId"}""")
                    val state = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                    state["played"]!!.jsonPrimitive.boolean shouldBe true
                }
            }
        }

        it("POST /{id}/played returns 404 for an unknown episode") {
            testApp { json, _ ->
                json.post("/api/episodes/nonexistent/played").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("Settings") {
        it("PUT persists and GET reflects the change") {
            testApp { json, _ ->
                json.put("/api/settings") {
                    contentType(ContentType.Application.Json)
                    setBody(SettingsDto(hidePlayed = true))
                }.status shouldBe HttpStatusCode.NoContent

                json.get("/api/settings").body<SettingsDto>().hidePlayed shouldBe true
            }
        }

        it("hidePlayed=true filters played episodes from podcast detail") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()

                json.post("/api/podcasts/${podcast.id}/played")
                json.put("/api/settings") {
                    contentType(ContentType.Application.Json)
                    setBody(SettingsDto(hidePlayed = true))
                }

                json.get("/api/podcasts/${podcast.id}").body<PodcastDetailDto>().episodes.shouldBeEmpty()
            }
        }
    }

    describe("Queue") {
        it("add and remove episodes") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val ep1 = podcast.episodes[0].id
                val ep2 = podcast.episodes[1].id

                json.post("/api/queue/${ep1.encodeURLPathPart()}")
                json.post("/api/queue/${ep2.encodeURLPathPart()}")

                json.get("/api/queue").body<List<EpisodeDetailDto>>().map { it.id } shouldBe listOf(ep1, ep2)

                json.delete("/api/queue/${ep1.encodeURLPathPart()}")

                json.get("/api/queue").body<List<EpisodeDetailDto>>().map { it.id } shouldBe listOf(ep2)
            }
        }
    }

    describe("Playback WebSocket") {
        it("update, ended marks played, start resets") {
            testApp { _, ws ->
                ws.webSocket("/api/playback") {
                    send("""{"type":"update","episodeId":"ep-1","progressMs":30000}""")
                    send("""{"type":"ended","episodeId":"ep-1"}""")
                    send("""{"type":"get","episodeId":"ep-1"}""")
                    val afterEnded = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                    afterEnded["played"]!!.jsonPrimitive.boolean shouldBe true
                    afterEnded["progressMs"]!!.jsonPrimitive.long shouldBe 30000L

                    send("""{"type":"start","episodeId":"ep-1","startPositionMs":0}""")
                    send("""{"type":"get","episodeId":"ep-1"}""")
                    val afterStart = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                    afterStart["played"]!!.jsonPrimitive.boolean shouldBe false
                    afterStart["progressMs"]!!.jsonPrimitive.long shouldBe 0L
                }
            }
        }
    }

    describe("OPML import") {
        it("imports all podcasts from a valid OPML file") {
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
            testApp(rssFeeds = mapOf(
                feed1 to "<rss><channel><title>Show 1</title><image><url>img1</url></image></channel></rss>",
                feed2 to "<rss><channel><title>Show 2</title><image><url>img2</url></image></channel></rss>",
            )) { json, _ ->
                json.post("/api/podcasts/import") {
                    setBody(MultiPartFormDataContent(formData {
                        append("opml", opml, Headers.build {
                            append(HttpHeaders.ContentDisposition, """form-data; name="opml"; filename="subscriptions.opml"""")
                        })
                    }))
                }.status shouldBe HttpStatusCode.OK

                val podcasts = json.get("/api/podcasts").body<List<PodcastSummaryDto>>()
                podcasts.map { it.url }.toSet() shouldBe setOf(feed1, feed2)
            }
        }

        it("returns 400 when no file is uploaded") {
            testApp { json, _ ->
                json.post("/api/podcasts/import") {
                    setBody(MultiPartFormDataContent(formData {}))
                }.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }
})

private fun Application.installInMemoryDatabase() {
    val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
    connection.createStatement().use { stmt ->
        listOf(
            CREATE_PODCASTS_TABLE,
            CREATE_EPISODES_TABLE,
            CREATE_PLAYBACK_STATE_TABLE,
            CREATE_QUEUE_TABLE,
            CREATE_SETTINGS_TABLE,
        ).forEach { stmt.executeUpdate(it) }
    }
    monitor.subscribe(ApplicationStopped) { connection.close() }
    val db = SingleConnectionProvider(connection)
    dependencies { provide<ConnectionProvider> { db } }
}
