package playback

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import playback.adapters.persistence.SQLitePlaybackState
import playback.core.ports.PlaybackPersistence
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.GetPlaybackStates
import playback.core.usecase.MarkAllPlayed
import playback.core.usecase.MarkPlayed
import playback.core.usecase.UpdateProgress

fun Application.installPlaybackModule(
    playbackState: PlaybackPersistence? = null
) {
    dependencies {
        provide<PlaybackPersistence> { playbackState ?: SQLitePlaybackState(resolve()) }

        provide<GetPlaybackState> { GetPlaybackState(resolve(), resolve()) }
        provide<GetPlaybackStates> { GetPlaybackStates(resolve()) }
        provide<UpdateProgress> { UpdateProgress(resolve(), resolve()) }
        provide<MarkPlayed> { MarkPlayed(resolve()) }
        provide<MarkAllPlayed> { MarkAllPlayed(resolve()) }
    }
}