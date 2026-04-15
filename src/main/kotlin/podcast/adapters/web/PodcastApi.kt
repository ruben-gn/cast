package podcast.adapters.web

import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import podcast.core.model.Episode
import podcast.core.model.Podcast
import podcast.core.AddFeed
import podcast.core.GetPodcast
import podcast.core.ListPodcasts

fun Route.podcastApi(dependencies: DependencyRegistry) {

    val addFeed: AddFeed by dependencies
    val listPodcasts: ListPodcasts by dependencies
    val getPodcast: GetPodcast by dependencies

    route("podcasts") {
        get {
            val podcasts = listPodcasts().map(::podcastSummaryDto)
            call.respond(podcasts)
        }

        post {
            val request = call.receive<AddPodcastRequest>()
            val podcast = addFeed(url = request.feed)
            call.respond(podcastDetailDto(podcast))
        }

        get("{id}") {
            val id = call.parameters["id"]!!
            val podcast = getPodcast(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.respond(podcastDetailDto(podcast))
        }
    }
}

private fun podcastSummaryDto(podcast: Podcast) =
    PodcastSummaryDto(podcast.id, podcast.url, podcast.name, podcast.image, podcast.createdAt.toString())

private fun podcastDetailDto(podcast: Podcast) =
    PodcastDetailDto(
        id = podcast.id,
        url = podcast.url,
        name = podcast.name,
        image = podcast.image,
        createdAt = podcast.createdAt.toString(),
        episodes = podcast.episodes.map(::episodeDto)
    )

private fun episodeDto(episode: Episode) =
    EpisodeDto(
        id = episode.id,
        title = episode.title,
        description = episode.description,
        audioUrl = episode.audioUrl,
        duration = episode.duration,
        publishedAt = episode.publishedAt?.toString()
    )

@Serializable
data class AddPodcastRequest(val feed: String)

@Serializable
data class PodcastSummaryDto(val id: String, val url: String, val name: String, val image: String, val createdAt: String)

@Serializable
data class EpisodeDto(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: String?,
    val publishedAt: String?
)

@Serializable
data class PodcastDetailDto(
    val id: String,
    val url: String,
    val name: String,
    val image: String,
    val createdAt: String,
    val episodes: List<EpisodeDto>
)
