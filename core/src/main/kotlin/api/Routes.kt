package api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.installRoutes() {
    routing {
        head("/health") { call.respond(HttpStatusCode.OK) }
        route("/api") {
            route("/podcasts") { podcastApi(dependencies) }
            route("/playback") { playbackApi(dependencies) }
            route("/queue") { queueApi(dependencies) }
            route("/settings") { settingsApi(dependencies) }
            route("/episodes") { episodeApi(dependencies) }
        }
    }
}
