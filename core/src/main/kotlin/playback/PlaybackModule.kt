package playback

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import playback.core.UpdatePlaybackState
import java.time.Clock


fun Application.installPlaybackModule(
    clock: Clock = Clock.systemUTC(),
) {
    dependencies {
        provide<UpdatePlaybackState> { UpdatePlaybackState(resolve(), resolve()) }
    }

    routing {

    }
}