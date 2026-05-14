import api.installRoutes
import application.installApplicationModule
import configuration.installDatabase
import io.ktor.server.application.*
import io.ktor.server.netty.*
import playback.installPlaybackModule
import podcast.installPodcastModule
import queue.installQueueModule

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    installHttpClient()
    installDatabase()

    installCommon()

    installPodcastModule()
    installPlaybackModule()
    installQueueModule()

    installApplicationModule()

    installRoutes()
}
