package playback.adapters.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.UpdatePlaybackState

private val log = KotlinLogging.logger { }
private val json = Json

fun Route.playbackApi(dependencies: DependencyRegistry) {
    val updatePlaybackState: UpdatePlaybackState by dependencies
    val getPlaybackState: GetPlaybackState by dependencies

    webSocket {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                try {
                    val text = frame.readText()
                    log.info { "Received playback message: $text" }
                    val obj = json.parseToJsonElement(text).jsonObject
                    val episodeId = obj["episodeId"]!!.jsonPrimitive.content
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "update" -> {
                            val progressMs = obj["progressMs"]!!.jsonPrimitive.long
                            updatePlaybackState(episodeId = episodeId, progressMs = progressMs)
                        }
                        "get" -> {
                            val state = getPlaybackState(episodeId)
                            send(json.encodeToString(PlaybackStateResponse(
                                type = "state",
                                episodeId = state.episodeId.value,
                                progressMs = state.progressMs
                            )))
                        }
                        else -> log.warn { "Unknown message type in: $text" }
                    }
                } catch (e: Exception) {
                    log.error(e) { "Failed to handle playback message. Continuing..." }
                }
            }
        }
    }
}

@Serializable
data class PlaybackStateResponse(
    val type: String,
    val episodeId: String,
    val progressMs: Long
)
