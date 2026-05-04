package playback

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import playback.adapters.api.playbackApi
import playback.adapters.persistence.SQLitePlaybackState
import playback.core.ports.PlaybackPersistence
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.MarkPlayed
import playback.core.usecase.UpdateProgress

fun Application.installPlaybackModule(
    playbackState: PlaybackPersistence? = null
) {
    dependencies {
        provide<PlaybackPersistence> { playbackState ?: SQLitePlaybackState(resolve()) }

        provide<GetPlaybackState> { GetPlaybackState(resolve(), resolve()) }
        provide<UpdateProgress> { UpdateProgress(resolve(), resolve()) }
        provide<MarkPlayed> { MarkPlayed(resolve()) }
    }

    routing {
        route("/api/playback") { playbackApi(dependencies) }
    }
}