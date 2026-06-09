package api

import application.model.EpisodeWithPlayback
import application.usecase.GetPodcastDetail
import application.usecase.RemovePodcast
import cast.api.AddPodcastRequest
import cast.api.EpisodeDetailDto
import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import playback.core.usecase.MarkAllPlayed
import podcast.core.PodcastException
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.usecase.AddFeed
import podcast.core.usecase.ImportOpml
import podcast.core.usecase.ListEpisodes
import podcast.core.usecase.ListPodcasts
import podcast.core.usecase.StartListening
import podcast.core.usecase.StopListening
import kotlin.time.Duration

fun Route.podcastApi(dependencies: DependencyRegistry) {

    val addFeed: AddFeed by dependencies
    val importOpml: ImportOpml by dependencies
    val listPodcasts: ListPodcasts by dependencies
    val listEpisodes: ListEpisodes by dependencies
    val getPodcastDetail: GetPodcastDetail by dependencies
    val markAllPlayed: MarkAllPlayed by dependencies
    val removePodcast: RemovePodcast by dependencies
    val startListening: StartListening by dependencies
    val stopListening: StopListening by dependencies

    get {
        call.respond(listPodcasts().map(::podcastSummaryDto))
    }

    post {
        val request = call.receive<AddPodcastRequest>()
        try {
            val podcast = addFeed(url = FeedUrl(request.feed))
            val episodes = listEpisodes(podcast.id)
            call.respond(podcastDetailDto(podcast, episodes.map { EpisodeWithPlayback(it, 0, false) }))
        } catch (e: PodcastException.FeedFetchFailed) {
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to (e.message ?: "Failed to fetch feed")))
        }
    }

    post("import") {
        val multipart = call.receiveMultipart()
        var opmlContent: ByteArray? = null
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) opmlContent = part.provider().toByteArray()
            part.dispose()
        }
        val content = opmlContent ?: return@post call.respond(HttpStatusCode.BadRequest)
        val result = importOpml(content)
        call.respond(mapOf("imported" to result.imported.size, "failed" to result.failed.size))
    }

    get("{id}") {
        val id = PodcastId(call.parameters["id"]!!)
        val detail = getPodcastDetail(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(podcastDetailDto(detail.podcast, detail.episodes))
    }

    delete("{id}") {
        val id = PodcastId(call.parameters["id"]!!)
        val removed = removePodcast(id)
        call.respond(if (removed) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }

    post("{id}/played") {
        val id = PodcastId(call.parameters["id"]!!)
        val detail = getPodcastDetail(id) ?: return@post call.respond(HttpStatusCode.NotFound)
        markAllPlayed(detail.episodes.map { it.episode.id })
        call.respond(HttpStatusCode.NoContent)
    }

    post("{id}/listening") {
        val id = PodcastId(call.parameters["id"]!!)
        val found = startListening(id)
        call.respond(if (found) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }

    delete("{id}/listening") {
        val id = PodcastId(call.parameters["id"]!!)
        val found = stopListening(id)
        call.respond(if (found) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }
}

private fun podcastSummaryDto(podcast: Podcast) =
    PodcastSummaryDto(
        id = podcast.id.value,
        url = podcast.url.value,
        name = podcast.name,
        image = podcast.image,
        listening = podcast.listening,
        created = podcast.created.toString(),
        latestEpisodeAt = podcast.latestEpisodeAt.toString(),
    )

private fun podcastDetailDto(podcast: Podcast, episodes: List<EpisodeWithPlayback>) =
    PodcastDetailDto(
        id = podcast.id.value,
        url = podcast.url.value,
        name = podcast.name,
        image = podcast.image,
        listening = podcast.listening,
        created = podcast.created.toString(),
        episodes = episodes.map(::episodeDetailDto)
    )

internal fun episodeDetailDto(
    ep: EpisodeWithPlayback,
    podcastId: String? = null,
    podcastName: String? = null,
    podcastImage: String? = null,
) = EpisodeDetailDto(
    id = ep.episode.id.value,
    title = ep.episode.title,
    description = ep.episode.description,
    audioUrl = ep.episode.audioUrl,
    duration = ep.episode.duration?.formatted(),
    durationMs = ep.episode.duration?.inWholeMilliseconds,
    publishedAt = ep.episode.publishedAt?.toString(),
    played = ep.played,
    progressMs = ep.progressMs,
    podcastId = podcastId,
    podcastName = podcastName,
    podcastImage = podcastImage,
)

internal fun Duration.formatted(): String =
    toComponents { _, hours, minutes, seconds, _ ->
        if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
