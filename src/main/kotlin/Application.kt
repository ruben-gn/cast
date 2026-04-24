import configuration.installDatabase
import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import playback.installPlaybackModule
import podcast.PODCAST_ROUTE
import podcast.installPodcastModule
import java.time.Clock

fun main(args: Array<String>) {
    EngineMain.main(args)
}

fun Application.module() {
    installHttpClient()
    installDatabase()

    installCommon()
    installDefaultRouting()

    // modules
    installPodcastModule()
    installPlaybackModule()
}

fun Application.installHttpClient(httpClient: HttpClient? = null) {
    val client = httpClient
        ?: HttpClient { install(UserAgent) { agent = "Cast/1.0" } }
            .also { client -> monitor.subscribe(ApplicationStopped) { client.close() } }

    dependencies {
        provide<HttpClient> { client }
    }
}

fun Application.installDefaultRouting() {
    routing {
        staticResources("/static", "static")
        get("/") {
            call.respondRedirect("/$PODCAST_ROUTE/")
        }
    }
}

fun Application.installCommon(clock: Clock = Clock.systemUTC()) {
    install(ContentNegotiation) {
        json()
    }

    install(IgnoreTrailingSlash)

    dependencies {
        provide<Clock> { clock }
    }
}
