package podcast.adapters.web

import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import podcast.core.model.Podcast
import podcast.core.usecase.AddFeed
import podcast.core.usecase.ListPodcasts

fun Route.podcastRouting(dependencies: DependencyRegistry) {

    val addFeed: AddFeed by dependencies
    val listPodcasts: ListPodcasts by dependencies

    route("podcasts") {
        get {
            val podcasts = listPodcasts().map(::podcastDto)
            call.respond(podcasts)
        }

        post {
            val request = call.receive<AddPodcastRequest>()
            val podcast = addFeed(url = request.feed).let(::podcastDto)
            call.respond(podcast)
        }
    }
}

fun podcastDto(podcast: Podcast): PodcastDto = PodcastDto(podcast.id, podcast.url, podcast.name, podcast.image, podcast.createdAt.toString())

@Serializable
data class AddPodcastRequest(val feed: String)

@Serializable
data class PodcastDto(val id: String, val url: String, val name: String, val image: String, val createdAt: String)