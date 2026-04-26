package playback.adapters.api

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import playback.core.usecase.UpdatePlaybackState

private val log = KotlinLogging.logger { }

fun Route.playbackApi(dependencies: DependencyRegistry) {
    val updatePlaybackState: UpdatePlaybackState by dependencies

    webSocket {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                try {
                    log.info { "Received playback progress update: ${frame.readText()}" }
                    val (episodeId, progressMs) = Json.decodeFromString<UpdatePlaybackProgressRequest>(frame.readText())
                    updatePlaybackState(episodeId = episodeId, progressMs = progressMs)
                } catch (e: Exception) {
                    log.error(e) { "Failed to update playback state. Continuing..." }
                }
            }
        }
    }
}

@Serializable
data class UpdatePlaybackProgressRequest(
    val episodeId: String,
    val progressMs: Long
)