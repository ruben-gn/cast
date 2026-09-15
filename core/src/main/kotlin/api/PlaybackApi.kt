package api

import application.usecase.RecordProgress
import cast.api.EpisodeEndedAckMessage
import cast.api.EpisodeEndedMessage
import cast.api.GetPlaybackStateMessage
import cast.api.PlaybackClientMessage
import cast.api.PlaybackServerMessage
import cast.api.PlaybackStateResponse
import cast.api.ProgressAckMessage
import cast.api.StartPlaybackMessage
import cast.api.UpdateProgressMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.MarkPlayed
import playback.core.usecase.StartPlayback
import shared.model.EpisodeId
import java.time.Instant

private val log = KotlinLogging.logger { }
// Tolerant of unknown fields so a newer client rolling out ahead of the server isn't rejected.
private val json = Json { ignoreUnknownKeys = true }

fun Route.playbackApi(dependencies: DependencyRegistry) {
    val recordProgress: RecordProgress by dependencies
    val getPlaybackState: GetPlaybackState by dependencies
    val markPlayed: MarkPlayed by dependencies
    val startPlayback: StartPlayback by dependencies

    webSocket {
        for (frame in incoming) {
            if (frame is Frame.Text) {
                try {
                    val text = frame.readText()
                    log.info { "Received playback message: $text" }
                    when (val message = json.decodeFromString<PlaybackClientMessage>(text)) {
                        is StartPlaybackMessage -> startPlayback(
                            episodeId = EpisodeId(message.episodeId),
                            startPositionMs = message.startPositionMs,
                        )
                        is UpdateProgressMessage -> {
                            recordProgress(
                                episodeId = EpisodeId(message.episodeId),
                                progressMs = message.progressMs,
                                updatedAt = message.updatedAt?.let(Instant::ofEpochMilli),
                            )
                            send(json.encodeToString<PlaybackServerMessage>(
                                ProgressAckMessage(message.episodeId, message.updatedAt)
                            ))
                        }
                        is EpisodeEndedMessage -> {
                            markPlayed(EpisodeId(message.episodeId))
                            send(json.encodeToString<PlaybackServerMessage>(
                                EpisodeEndedAckMessage(message.episodeId)
                            ))
                        }
                        is GetPlaybackStateMessage -> {
                            val state = getPlaybackState(EpisodeId(message.episodeId))
                            send(json.encodeToString<PlaybackServerMessage>(PlaybackStateResponse(
                                episodeId = state.episodeId.value,
                                progressMs = state.progressMs,
                                played = state.played,
                            )))
                        }
                    }
                } catch (e: Exception) {
                    log.error(e) { "Failed to handle playback message. Continuing..." }
                }
            }
        }
    }
}
