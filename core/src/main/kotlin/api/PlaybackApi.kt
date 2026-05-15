package api

import cast.api.PlaybackStateResponse
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.*
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.MarkPlayed
import playback.core.usecase.StartPlayback
import playback.core.usecase.UpdateProgress
import shared.model.EpisodeId

private val log = KotlinLogging.logger { }
private val json = Json

fun Route.playbackApi(dependencies: DependencyRegistry) {
    val updateProgress: UpdateProgress by dependencies
    val getPlaybackState: GetPlaybackState by dependencies
    val markPlayed: MarkPlayed by dependencies
    val startPlayback: StartPlayback by dependencies

    webSocket {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                try {
                    val text = frame.readText()
                    log.info { "Received playback message: $text" }
                    val obj = json.parseToJsonElement(text).jsonObject
                    val episodeId = EpisodeId(obj["episodeId"]!!.jsonPrimitive.content)
                    when (obj["type"]?.jsonPrimitive?.content) {
                        "start" -> {
                            val startPositionMs = obj["startPositionMs"]!!.jsonPrimitive.long
                            startPlayback(episodeId = episodeId, startPositionMs = startPositionMs)
                        }
                        "update" -> {
                            val progressMs = obj["progressMs"]!!.jsonPrimitive.long
                            updateProgress(episodeId = episodeId, progressMs = progressMs)
                        }
                        "ended" -> markPlayed(episodeId)
                        "get" -> {
                            val state = getPlaybackState(episodeId)
                            send(json.encodeToString(PlaybackStateResponse(
                                type = "state",
                                episodeId = state.episodeId.value,
                                progressMs = state.progressMs,
                                played = state.played,
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
