package api

import installCommon
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import playback.fakes.FakePlaybackPersistence
import playback.installPlaybackModule

class PlaybackApiTest : DescribeSpec({
    describe("Playback WebSocket API") {

        fun testApp(block: suspend DefaultClientWebSocketSession.() -> Unit) = testApplication {
            application {
                installCommon()
                installPlaybackModule(playbackState = FakePlaybackPersistence())
                routing { route("/api/playback") { playbackApi(dependencies) } }
            }
            val client = createClient { install(WebSockets.Plugin) }
            client.webSocket("/api/playback") { block() }
        }

        it("get returns correct response shape with default values") {
            testApp {
                send("""{"type":"get","episodeId":"ep-1"}""")
                val json = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                json["type"]!!.jsonPrimitive.content shouldBe "state"
                json["episodeId"]!!.jsonPrimitive.content shouldBe "ep-1"
                json["progressMs"]!!.jsonPrimitive.long shouldBe 0L
                json["played"]!!.jsonPrimitive.boolean shouldBe false
            }
        }

        it("update routes to updateProgress and get reflects new position") {
            testApp {
                send("""{"type":"update","episodeId":"ep-1","progressMs":42000}""")
                send("""{"type":"get","episodeId":"ep-1"}""")
                val json = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                json["progressMs"]!!.jsonPrimitive.long shouldBe 42000L
                json["played"]!!.jsonPrimitive.boolean shouldBe false
            }
        }

        it("ended routes to markPlayed and get reflects played=true") {
            testApp {
                send("""{"type":"ended","episodeId":"ep-1"}""")
                send("""{"type":"get","episodeId":"ep-1"}""")
                val json = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                json["played"]!!.jsonPrimitive.boolean shouldBe true
            }
        }

        it("update after ended does not reset played") {
            testApp {
                send("""{"type":"ended","episodeId":"ep-1"}""")
                send("""{"type":"update","episodeId":"ep-1","progressMs":1000}""")
                send("""{"type":"get","episodeId":"ep-1"}""")
                val json = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                json["played"]!!.jsonPrimitive.boolean shouldBe true
            }
        }

        it("start resets played to false and sets position") {
            testApp {
                send("""{"type":"ended","episodeId":"ep-1"}""")
                send("""{"type":"start","episodeId":"ep-1","startPositionMs":5000}""")
                send("""{"type":"get","episodeId":"ep-1"}""")
                val json = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                json["progressMs"]!!.jsonPrimitive.long shouldBe 5000L
                json["played"]!!.jsonPrimitive.boolean shouldBe false
            }
        }

        it("start at position 0 resets a played episode from the beginning") {
            testApp {
                send("""{"type":"ended","episodeId":"ep-1"}""")
                send("""{"type":"start","episodeId":"ep-1","startPositionMs":0}""")
                send("""{"type":"get","episodeId":"ep-1"}""")
                val json = Json.parseToJsonElement((incoming.receive() as Frame.Text).readText()).jsonObject
                json["progressMs"]!!.jsonPrimitive.long shouldBe 0L
                json["played"]!!.jsonPrimitive.boolean shouldBe false
            }
        }
    }
})
