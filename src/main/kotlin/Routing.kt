import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.installDefaultRouting() {
    routing {
        get("/") {
            call.respondRedirect("/podcasts/")
        }
    }
}