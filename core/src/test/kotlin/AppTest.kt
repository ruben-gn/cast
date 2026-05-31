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

    describe("the podcast catalog") {
        it("includes a podcast after subscribing via RSS URL") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()

                podcast.name shouldBe "Test Show"
                podcast.episodes.map { it.title } shouldBe listOf("Episode 1", "Episode 2")
                json.get("/api/podcasts").body<List<PodcastSummaryDto>>()
                    .map { it.url } shouldBe listOf(feedUrl)
            }
        }

        it("includes all feeds after an OPML import") {
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
                }
                json.get("/api/podcasts").body<List<PodcastSummaryDto>>()
                    .map { it.url }.toSet() shouldBe setOf(feed1, feed2)
            }
        }
    }

    describe("the recent feed") {
        it("shows unplayed episodes from subscribed podcasts") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()

                json.get("/api/episodes/recent").body<List<EpisodeDetailDto>>()
                    .map { it.title } shouldBe podcast.episodes.map { it.title }
            }
        }

        it("excludes episodes that have been played") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val episodeId = podcast.episodes.first().id

                json.post("/api/episodes/${episodeId.encodeURLPathPart()}/played")

                json.get("/api/episodes/recent").body<List<EpisodeDetailDto>>()
                    .none { it.id == episodeId } shouldBe true
            }
        }

        it("includes partial playback progress") {
            testApp { json, ws ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val episodeId = podcast.episodes.first().id

                ws.webSocket("/api/playback") {
                    send("""{"type":"update","episodeId":"$episodeId","progressMs":15000}""")
                    send("""{"type":"get","episodeId":"$episodeId"}""")
                    receiveState()
                }

                json.get("/api/episodes/recent").body<List<EpisodeDetailDto>>()
                    .first { it.id == episodeId }.progressMs shouldBe 15000L
            }
        }

        it("excludes episodes published more than two weeks ago") {
            val recentFeedUrl = "https://example.com/cutoff-feed.xml"
            val cutoffRss = """
                <rss><channel>
                    <title>Cutoff Show</title>
                    <image><url>https://example.com/img.png</url></image>
                    <item><title>Recent Episode</title><guid>cutoff-recent</guid><pubDate>05 Apr 2026 12:00:00 +0000</pubDate><enclosure url="https://cdn/recent.mp3" length="0" type="audio/mpeg"/></item>
                    <item><title>Old Episode</title><guid>cutoff-old</guid><pubDate>01 Mar 2026 12:00:00 +0000</pubDate><enclosure url="https://cdn/old.mp3" length="0" type="audio/mpeg"/></item>
                </channel></rss>
            """.trimIndent()
            testApp(rssFeeds = mapOf(recentFeedUrl to cutoffRss)) { json, _ ->
                json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(recentFeedUrl))
                }

                val titles = json.get("/api/episodes/recent").body<List<EpisodeDetailDto>>().map { it.title }
                titles shouldBe listOf("Recent Episode")
            }
        }
    }

    describe("GET /api/episodes/{episodeId}") {
        it("returns 404 for an unknown episode") {
            testApp { json, _ ->
                json.get("/api/episodes/nonexistent").status shouldBe HttpStatusCode.NotFound
            }
        }

        it("returns episode metadata including podcast name") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val episodeId = podcast.episodes.first().id

                val episode = json.get("/api/episodes/${episodeId.encodeURLPathPart()}").body<EpisodeDetailDto>()

                episode.id shouldBe episodeId
                episode.title shouldBe "Episode 1"
                episode.podcastName shouldBe "Test Show"
            }
        }

        it("returns partial playback progress") {
            testApp { json, ws ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val episodeId = podcast.episodes.first().id

                ws.webSocket("/api/playback") {
                    send("""{"type":"update","episodeId":"$episodeId","progressMs":30000}""")
                    send("""{"type":"get","episodeId":"$episodeId"}""")
                    receiveState()
                }

                val episode = json.get("/api/episodes/${episodeId.encodeURLPathPart()}").body<EpisodeDetailDto>()
                episode.progressMs shouldBe 30000L
                episode.played shouldBe false
            }
        }

        it("reflects the played state") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val episodeId = podcast.episodes.first().id

                json.post("/api/episodes/${episodeId.encodeURLPathPart()}/played")

                val episode = json.get("/api/episodes/${episodeId.encodeURLPathPart()}").body<EpisodeDetailDto>()
                episode.played shouldBe true
            }
        }
    }

    describe("an episode's played state") {
        it("can be set for all episodes in a podcast at once") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()

                json.post("/api/podcasts/${podcast.id}/played")

                json.get("/api/podcasts/${podcast.id}").body<PodcastDetailDto>()
                    .episodes.all { it.played } shouldBe true
            }
        }

        it("can be marked as played") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val episodeId = podcast.episodes.first().id

                json.post("/api/episodes/${episodeId.encodeURLPathPart()}/played")

                json.get("/api/podcasts/${podcast.id}").body<PodcastDetailDto>()
                    .episodes.first { it.id == episodeId }.played shouldBe true
            }
        }

        it("can be cleared after being played") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val episodeId = podcast.episodes.first().id

                json.post("/api/episodes/${episodeId.encodeURLPathPart()}/played")
                json.delete("/api/episodes/${episodeId.encodeURLPathPart()}/played")

                json.get("/api/podcasts/${podcast.id}").body<PodcastDetailDto>()
                    .episodes.first { it.id == episodeId }.played shouldBe false
            }
        }
    }

    describe("playback") {
        it("persists progress across sessions") {
            testApp { _, ws ->
                ws.webSocket("/api/playback") {
                    send("""{"type":"update","episodeId":"ep-1","progressMs":45000}""")
                    send("""{"type":"get","episodeId":"ep-1"}""")
                    receiveState()["progressMs"]!!.jsonPrimitive.long shouldBe 45000L
                }
                ws.webSocket("/api/playback") {
                    send("""{"type":"get","episodeId":"ep-1"}""")
                    receiveState()["progressMs"]!!.jsonPrimitive.long shouldBe 45000L
                }
            }
        }

        it("marks the episode as played when it ends") {
            testApp { _, ws ->
                ws.webSocket("/api/playback") {
                    send("""{"type":"ended","episodeId":"ep-1"}""")
                    send("""{"type":"get","episodeId":"ep-1"}""")
                    receiveState()["played"]!!.jsonPrimitive.boolean shouldBe true
                }
            }
        }

        it("resets progress and played state when starting over") {
            testApp { _, ws ->
                ws.webSocket("/api/playback") {
                    send("""{"type":"update","episodeId":"ep-1","progressMs":30000}""")
                    send("""{"type":"ended","episodeId":"ep-1"}""")
                    send("""{"type":"start","episodeId":"ep-1","startPositionMs":0}""")
                    send("""{"type":"get","episodeId":"ep-1"}""")
                    val state = receiveState()
                    state["played"]!!.jsonPrimitive.boolean shouldBe false
                    state["progressMs"]!!.jsonPrimitive.long shouldBe 0L
                }
            }
        }
    }

    describe("the queue") {
        it("preserves insertion order") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val ep1 = podcast.episodes[0].id
                val ep2 = podcast.episodes[1].id

                json.post("/api/queue/${ep1.encodeURLPathPart()}")
                json.post("/api/queue/${ep2.encodeURLPathPart()}")

                json.get("/api/queue").body<List<EpisodeDetailDto>>()
                    .map { it.id } shouldBe listOf(ep1, ep2)
            }
        }

        it("allows removing individual episodes") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val ep1 = podcast.episodes[0].id
                val ep2 = podcast.episodes[1].id

                json.post("/api/queue/${ep1.encodeURLPathPart()}")
                json.post("/api/queue/${ep2.encodeURLPathPart()}")
                json.delete("/api/queue/${ep1.encodeURLPathPart()}")

                json.get("/api/queue").body<List<EpisodeDetailDto>>()
                    .map { it.id } shouldBe listOf(ep2)
            }
        }

        it("allows reordering episodes") {
            testApp { json, _ ->
                val podcast = json.post("/api/podcasts") {
                    contentType(ContentType.Application.Json)
                    setBody(AddPodcastRequest(feedUrl))
                }.body<PodcastDetailDto>()
                val ep1 = podcast.episodes[0].id
                val ep2 = podcast.episodes[1].id

                json.post("/api/queue/${ep1.encodeURLPathPart()}")
                json.post("/api/queue/${ep2.encodeURLPathPart()}")

                json.put("/api/queue") {
                    contentType(ContentType.Application.Json)
                    setBody(ReorderQueueRequest(listOf(ep2, ep1)))
                }

                json.get("/api/queue").body<List<EpisodeDetailDto>>()
                    .map { it.id } shouldBe listOf(ep2, ep1)
            }
        }
    }

    describe("the hide-played setting") {
        it("filters played episodes from the podcast episode list") {
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

                json.get("/api/podcasts/${podcast.id}").body<PodcastDetailDto>()
                    .episodes.shouldBeEmpty()
            }
        }
    }
})

private suspend fun DefaultClientWebSocketSession.receiveState(): JsonObject =
    Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject

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
