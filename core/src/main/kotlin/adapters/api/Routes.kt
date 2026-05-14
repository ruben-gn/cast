package adapters.api

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*

fun Application.installRoutes() {
    routing {
        route("/api/podcasts") { podcastApi(dependencies) }
        route("/api/playback") { playbackApi(dependencies) }
        route("/api/queue") { queueApi(dependencies) }
    }
}
