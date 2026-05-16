package api

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*

fun Application.installRoutes() {
    routing {
        route("/api") {
            route("/podcasts") { podcastApi(dependencies) }
            route("/playback") { playbackApi(dependencies) }
            route("/queue") { queueApi(dependencies) }
            route("/settings") { settingsApi(dependencies) }
            route("/episodes") { episodeApi(dependencies) }
        }
    }
}
