package podcast.adapters.api

import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import podcast.PODCAST_ROUTE
import podcast.core.AddFeed
import podcast.core.GetPodcast
import podcast.core.ListEpisodes
import podcast.core.ListPodcasts
import podcast.core.PodcastException
import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.adapters.web.formatted

fun Route.podcastApi(dependencies: DependencyRegistry) {

    val addFeed: AddFeed by dependencies
    val listPodcasts: ListPodcasts by dependencies
    val getPodcast: GetPodcast by dependencies
    val listEpisodes: ListEpisodes by dependencies

    route(PODCAST_ROUTE) {
        get {
            call.respond(listPodcasts().map(::podcastSummaryDto))
        }

        post {
            val request = call.receive<AddPodcastRequest>()
            try {
                val podcast = addFeed(url = FeedUrl(request.feed))
                val episodes = listEpisodes(podcast.id)
                call.respond(podcastDetailDto(podcast, episodes))
            } catch (e: PodcastException.FeedFetchFailed) {
                call.respond(HttpStatusCode.BadGateway, mapOf("error" to (e.message ?: "Failed to fetch feed")))
            }
        }

        get("{id}") {
            val id = PodcastId(call.parameters["id"]!!)
            val podcast = getPodcast(id) ?: return@get call.respond(HttpStatusCode.NotFound)
            val episodes = listEpisodes(id)
            call.respond(podcastDetailDto(podcast, episodes))
        }
    }
}

private fun podcastSummaryDto(podcast: Podcast) =
    PodcastSummaryDto(podcast.id.value, podcast.url.value, podcast.name, podcast.image, podcast.created.toString(), podcast.updated.toString())

private fun podcastDetailDto(podcast: Podcast, episodes: List<Episode>) =
    PodcastDetailDto(
        id = podcast.id.value,
        url = podcast.url.value,
        name = podcast.name,
        image = podcast.image,
        created = podcast.created.toString(),
        updated = podcast.updated.toString(),
        episodes = episodes.map(::episodeDto)
    )

private fun episodeDto(episode: Episode) =
    EpisodeDto(
        id = episode.id.value,
        title = episode.title,
        description = episode.description,
        audioUrl = episode.audioUrl,
        duration = episode.duration?.formatted(),
        publishedAt = episode.publishedAt?.toString()
    )

@Serializable
data class AddPodcastRequest(val feed: String)

@Serializable
data class PodcastSummaryDto(val id: String, val url: String, val name: String, val image: String, val created: String, val updated: String)

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
    val created: String,
    val updated: String,
    val episodes: List<EpisodeDto>
)
