package playback.adapters.api

import installCommon
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.testing.ApplicationTestBuilder
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

        fun testApp(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
            application {
                installCommon()
                installPlaybackModule(playbackState = FakePlaybackPersistence())
            }
            block()
        }

        it("should return zero progress for an episode with no saved state") {
            testApp {
                createClient { install(WebSockets.Plugin) }.webSocket("/api/playback") {
                    send("""{"type":"get","episodeId":"ep-unknown"}""")
                    val response = (incoming.receive() as Frame.Text).readText()
                    val json = Json.parseToJsonElement(response).jsonObject
                    json["type"]!!.jsonPrimitive.content shouldBe "state"
                    json["episodeId"]!!.jsonPrimitive.content shouldBe "ep-unknown"
                    json["progressMs"]!!.jsonPrimitive.long shouldBe 0L
                    json["played"]!!.jsonPrimitive.boolean shouldBe false
                }
            }
        }

        it("should return saved progress after an update in the same session") {
            testApp {
                createClient { install(WebSockets.Plugin) }.webSocket("/api/playback") {
                    send("""{"type":"update","episodeId":"ep-1","progressMs":12000}""")
                    send("""{"type":"get","episodeId":"ep-1"}""")
                    val response = (incoming.receive() as Frame.Text).readText()
                    val json = Json.parseToJsonElement(response).jsonObject
                    json["type"]!!.jsonPrimitive.content shouldBe "state"
                    json["episodeId"]!!.jsonPrimitive.content shouldBe "ep-1"
                    json["progressMs"]!!.jsonPrimitive.long shouldBe 12000L
                    json["played"]!!.jsonPrimitive.boolean shouldBe false
                }
            }
        }

        it("should mark episode as played when receiving ended") {
            testApp {
                createClient { install(WebSockets.Plugin) }.webSocket("/api/playback") {
                    send("""{"type":"ended","episodeId":"ep-1"}""")
                    send("""{"type":"get","episodeId":"ep-1"}""")
                    val response = (incoming.receive() as Frame.Text).readText()
                    val json = Json.parseToJsonElement(response).jsonObject
                    json["played"]!!.jsonPrimitive.boolean shouldBe true
                }
            }
        }

        it("should not reset played when progress update arrives after ended") {
            testApp {
                createClient { install(WebSockets.Plugin) }.webSocket("/api/playback") {
                    send("""{"type":"update","episodeId":"ep-1","progressMs":5000}""")
                    send("""{"type":"ended","episodeId":"ep-1"}""")
                    send("""{"type":"update","episodeId":"ep-1","progressMs":9000}""")
                    send("""{"type":"get","episodeId":"ep-1"}""")
                    val response = (incoming.receive() as Frame.Text).readText()
                    val json = Json.parseToJsonElement(response).jsonObject
                    json["progressMs"]!!.jsonPrimitive.long shouldBe 9000L
                    json["played"]!!.jsonPrimitive.boolean shouldBe true
                }
            }
        }
    }
})
