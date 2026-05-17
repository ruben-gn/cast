import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.httpMethod
import io.ktor.server.request.uri
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import org.slf4j.event.Level
import java.time.Clock
import java.util.UUID

fun Application.installHttpClient(httpClient: HttpClient? = null) {
    val client = httpClient
        ?: HttpClient {
            install(UserAgent) { agent = "Cast/1.0" }
            install(HttpRedirect)
        }
            .also { client -> monitor.subscribe(ApplicationStopped) { client.close() } }

    dependencies {
        provide<HttpClient> { client }
    }
}

fun Application.installCommon(clock: Clock = Clock.systemUTC()) {
    install(ContentNegotiation) {
        json()
    }

    install(CallLogging) {
        level = Level.INFO
        format { call ->
            "${call.request.httpMethod.value} ${call.request.uri} -> ${call.response.status()} (${call.processingTimeMillis()}ms)"
        }
        mdc("correlationId") { call ->
            call.request.headers["X-Correlation-Id"] ?: UUID.randomUUID().toString().take(8)
        }
    }

    install(IgnoreTrailingSlash)
    install(WebSockets)

    dependencies {
        provide<Clock> { clock }
    }
}
