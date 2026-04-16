package podcast.adapters.web.view

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.body
import podcast.adapters.web.view.components.layout
import podcast.adapters.web.view.components.podcastDetails
import podcast.adapters.web.view.components.podcastList
import podcast.core.GetPodcast
import podcast.core.ListPodcasts

fun Route.podcastView(dependencies: DependencyRegistry) {

    val listPodcasts: ListPodcasts by dependencies
    val getPodcast: GetPodcast by dependencies

    route("podcasts") {
        get {
            val podcasts = listPodcasts()
            val isHtmx = call.request.headers["HX-Request"] == "true"

            if (isHtmx) {
                call.respondHtml(HttpStatusCode.OK) {
                    body { podcastList(podcasts) }
                }
            } else {
                call.respondHtml {
                    layout("Cast") { podcastList(podcasts) }
                }
            }
        }

        get("{id}") {
            val id = call.parameters["id"]!!
            val podcast = getPodcast(id) ?: return@get call.respond(HttpStatusCode.NotFound, "Podcast not found")
            val isHtmx = call.request.headers["HX-Request"] == "true"

            if (isHtmx) {
                call.respondHtml(HttpStatusCode.OK) {
                    body { podcastDetails(podcast) }
                }
            } else {
                call.respondHtml {
                    layout(podcast.name) { podcastDetails(podcast) }
                }
            }
        }
    }
}