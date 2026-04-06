package grootnibbel.ink.podcast

import grootnibbel.ink.podcast.core.Podcast
import grootnibbel.ink.podcast.core.PodcastApi
import grootnibbel.ink.podcast.core.PodcastPersistence
import grootnibbel.ink.podcast.infrastructure.InMemoryPodcastPersistence
import io.ktor.server.application.*
import io.ktor.server.plugins.di.DependencyRegistry
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Application.podcastModule() {
    dependencies {
        provide<PodcastPersistence> { InMemoryPodcastPersistence() }
        provide<PodcastApi> { PodcastApi(resolve()) }
    }

    routing {
        podcastRouting(dependencies)
    }
}

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