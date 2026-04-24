import configuration.installDatabase
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import playback.installPlaybackModule
import podcast.PODCAST_ROUTE
import podcast.installPodcastModule
import routes.podcastView

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    installHttpClient()
    installDatabase()

    installCommon()
    installDefaultRouting()

    installPodcastModule()
    installPlaybackModule()
}

fun Application.installDefaultRouting() {
    routing {
        staticResources("/static", "static")
        get("/") {
            call.respondRedirect("/$PODCAST_ROUTE/")
        }
        podcastView(dependencies)
    }
}
