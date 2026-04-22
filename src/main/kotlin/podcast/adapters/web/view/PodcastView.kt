package podcast.adapters.web.view

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.div
import kotlinx.html.stream.appendHTML
import podcast.adapters.web.view.components.layout
import podcast.adapters.web.view.components.podcastDetails
import podcast.adapters.web.view.components.podcastList
import podcast.core.AddFeed
import podcast.core.GetPodcast
import podcast.core.ListPodcasts
import podcast.core.PodcastException

fun Route.podcastView(dependencies: DependencyRegistry) {

    val listPodcasts: ListPodcasts by dependencies
    val getPodcast: GetPodcast by dependencies
    val addFeed: AddFeed by dependencies

    route("podcasts") {
        get {
            val podcasts = listPodcasts()
            val isHtmx = call.request.headers["HX-Request"] == "true"

            if (isHtmx) {
                call.respondText(ContentType.Text.Html) {
                    buildString { appendHTML(false).div { attributes["id"] = "content-container"; podcastList(podcasts) } }
                }
            } else {
                call.respondHtml {
                    layout("Cast") { podcastList(podcasts) }
                }
            }
        }

        post {
            val params = call.receiveParameters()
            val url = params["url"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing URL")

            try {
                addFeed(url)
                val podcasts = listPodcasts()
                val isHtmx = call.request.headers["HX-Request"] == "true"

                if (isHtmx) {
                    call.respondText(ContentType.Text.Html) {
                        buildString { appendHTML(false).div { attributes["id"] = "content-container"; podcastList(podcasts) } }
                    }
                } else {
                    call.respondRedirect("/podcasts")
                }
            } catch (e: PodcastException.FeedFetchFailed) {
                call.respond(HttpStatusCode.BadGateway, "Failed to add feed: ${e.message}")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Unexpected error: ${e.message}")
            }
        }

        get("{id}") {
            val id = call.parameters["id"]!!
            val podcast = getPodcast(id) ?: return@get call.respond(HttpStatusCode.NotFound, "Podcast not found")
            val isHtmx = call.request.headers["HX-Request"] == "true"

            if (isHtmx) {
                call.respondText(ContentType.Text.Html) {
                    buildString { appendHTML(false).div { attributes["id"] = "content-container"; podcastDetails(podcast) } }
                }
            } else {
                call.respondHtml {
                    layout(podcast.name) { podcastDetails(podcast) }
                }
            }
        }
    }
}
