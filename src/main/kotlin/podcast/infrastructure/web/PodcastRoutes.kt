package podcast.infrastructure.web

import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import podcast.core.model.Podcast
import podcast.core.usecase.AddPodcast
import podcast.core.usecase.ListPodcasts

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

fun podcastDto(podcast: Podcast): PodcastDto = PodcastDto(podcast.id, podcast.url, podcast.name, podcast.image, podcast.createdAt.toString())

@Serializable
data class AddPodcastRequest(val url: String)

@Serializable
data class PodcastDto(val id: String, val url: String, val name: String, val image: String, val createdAt: String)