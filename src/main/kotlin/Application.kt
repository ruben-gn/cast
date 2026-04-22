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
import podcast.installPodcastModule

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
            call.respondRedirect("/podcasts/")
        }
    }
}

fun Application.installCommon() {
    install(ContentNegotiation) {
        json()
    }

    install(IgnoreTrailingSlash)
}
