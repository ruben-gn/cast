import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.*
import podcast.installPodcastModule

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

fun Application.installDefaultRouting() {
    routing {
        staticResources("/static", "static")
        get("/") {
            call.respondRedirect("/podcasts/")
        }
    }
}

fun Application.installCommon(httpClient: HttpClient? = null) {
    install(ContentNegotiation) {
        json()
    }

    install(IgnoreTrailingSlash)

    val client = httpClient ?: HttpClient() {
        install(UserAgent) { agent = "Cast/1.0" }
    }
    monitor.subscribe(ApplicationStopped) { client.close() }

    dependencies {
        provide<HttpClient> { client }
    }
}
