package api

import configuration.ConnectionProvider
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

private val log = KotlinLogging.logger { }

@Serializable
data class HealthResponse(val status: String, val database: String)

fun Application.installRoutes() {
    routing {
        val db: ConnectionProvider by dependencies
        head("/health") {
            call.respond(if (db.databaseUp()) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable)
        }
        get("/health") {
            val up = db.databaseUp()
            call.respond(
                if (up) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
                HealthResponse(status = if (up) "ok" else "degraded", database = if (up) "up" else "down"),
            )
        }
        route("/api") {
            route("/podcasts") { podcastApi(dependencies) }
            route("/playback") { playbackApi(dependencies) }
            route("/queue") { queueApi(dependencies) }
            route("/settings") { settingsApi(dependencies) }
            route("/episodes") { episodeApi(dependencies) }
        }
    }
}

private suspend fun ConnectionProvider.databaseUp(): Boolean =
    runCatching {
        withConnection { conn ->
            conn.createStatement().use { st ->
                st.executeQuery("SELECT 1").use { it.next() }
            }
        }
    }.onFailure { log.warn(it) { "Health check: database unreachable" } }.getOrDefault(false)
