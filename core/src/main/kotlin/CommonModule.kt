import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import java.time.Clock

fun Application.installHttpClient(httpClient: HttpClient? = null) {
    val client = httpClient
        ?: HttpClient { install(UserAgent) { agent = "Cast/1.0" } }
            .also { client -> monitor.subscribe(ApplicationStopped) { client.close() } }

    dependencies {
        provide<HttpClient> { client }
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
