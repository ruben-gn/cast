import io.ktor.client.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
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

fun Application.installCommon(httpClient: HttpClient? = null) {
    install(ContentNegotiation) {
        json()
    }

    install(IgnoreTrailingSlash)

    dependencies {
        provide<HttpClient> {
            httpClient ?: HttpClient() {
                install(io.ktor.client.plugins.UserAgent) {
                    agent = "Cast/1.0"
                }
            }
        }
    }
}
