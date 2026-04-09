package podcast.infrastructure.web

import podcast.core.model.Podcast
import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import podcast.core.usecase.AddPodcast
import podcast.core.usecase.ListPodcasts
import kotlin.collections.map

fun Route.podcastRouting(dependencies: DependencyRegistry) {

    val addPodcast: AddPodcast by dependencies
    val listPodcasts: ListPodcasts by dependencies

    route("podcasts") {
        get {
            val podcasts = listPodcasts().map(::podcastDto)
            call.respond(podcasts)
        }

        post {
            val request = call.receive<AddPodcastRequest>()
            val podcast = addPodcast(url = request.url).let(::podcastDto)
            call.respond(podcast)
        }
    }
}

fun podcastDto(podcast: Podcast): PodcastDto = PodcastDto(podcast.id, podcast.url, podcast.name, podcast.image)

@Serializable
data class AddPodcastRequest(val url: String)

@Serializable
data class PodcastDto(val id: String, val url: String, val name: String, val image: String)