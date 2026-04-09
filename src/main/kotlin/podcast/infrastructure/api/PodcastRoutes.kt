package grootnibbel.ink.podcast.infrastructure.html

import grootnibbel.ink.podcast.core.Podcast
import grootnibbel.ink.podcast.core.PodcastApi
import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import kotlin.collections.map

fun Route.podcastRouting(dependencies: DependencyRegistry) {

    val api: PodcastApi by dependencies

    route("podcasts") {
        get("/") {
            val podcasts = api.get().map(::podcastDto)
            call.respond(podcasts)
        }

        post("/") {
            val request = call.receive<AddPodcastRequest>()
            val podcast = api.add(url = request.url).let(::podcastDto)
            call.respond(podcast)
        }
    }
}

fun podcastDto(podcast: Podcast): PodcastDto = PodcastDto(podcast.id, podcast.url, podcast.name, podcast.image)

@Serializable
data class AddPodcastRequest(val url: String)

@Serializable
data class PodcastDto(val id: String, val url: String, val name: String, val image: String)