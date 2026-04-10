import podcast.installPodcastModule
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.IgnoreTrailingSlash

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    // cross cutting concerns
    installCommon()

    // application defaults
    installDefaultRouting()

    // modules
    installPodcastModule()
}

fun Application.installCommon() {
    install(ContentNegotiation) {
        json()
    }

    install(IgnoreTrailingSlash)
}
