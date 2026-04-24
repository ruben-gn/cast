package routes

import io.ktor.http.*
import io.ktor.server.application.ApplicationCall
import io.ktor.server.html.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.div
import kotlinx.html.stream.appendHTML
import layout
import podcast.podcastDetails
import podcast.podcastList
import podcast.core.AddFeed
import podcast.core.GetPodcast
import podcast.core.ListEpisodes
import podcast.core.ListPodcasts
import podcast.core.PodcastException
import podcast.core.models.FeedUrl
import podcast.core.models.PodcastId

val ApplicationCall.isHtmx: Boolean get() = request.headers["HX-Request"] == "true"

fun Route.podcastView(dependencies: DependencyRegistry) {

    val listPodcasts: ListPodcasts by dependencies
    val getPodcast: GetPodcast by dependencies
    val addFeed: AddFeed by dependencies
    val listEpisodes: ListEpisodes by dependencies

    route("podcasts") {
        get {
            val podcasts = listPodcasts()

            if (call.isHtmx) {
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
                addFeed(FeedUrl(url))
                val podcasts = listPodcasts()

                if (call.isHtmx) {
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
            val id = PodcastId(call.parameters["id"]!!)
            val podcast = getPodcast(id) ?: return@get call.respond(HttpStatusCode.NotFound, "Podcast not found")
            val episodes = listEpisodes(id)

            if (call.isHtmx) {
                call.respondText(ContentType.Text.Html) {
                    buildString { appendHTML(false).div { attributes["id"] = "content-container"; podcastDetails(podcast, episodes) } }
                }
            } else {
                call.respondHtml {
                    layout(podcast.name) { podcastDetails(podcast, episodes) }
                }
            }
        }
    }
}
