package api

import application.model.EpisodeWithPlayback
import application.usecase.GetPodcastDetail
import cast.api.AddPodcastRequest
import cast.api.EpisodeDetailDto
import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import podcast.core.PodcastException
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.usecase.AddFeed
import podcast.core.usecase.ListEpisodes
import podcast.core.usecase.ListPodcasts
import kotlin.time.Duration

fun Route.podcastApi(dependencies: DependencyRegistry) {

    val addFeed: AddFeed by dependencies
    val listPodcasts: ListPodcasts by dependencies
    val listEpisodes: ListEpisodes by dependencies
    val getPodcastDetail: GetPodcastDetail by dependencies

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

    get("{id}") {
        val id = PodcastId(call.parameters["id"]!!)
        val detail = getPodcastDetail(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(podcastDetailDto(detail.podcast, detail.episodes))
    }
}

private fun podcastSummaryDto(podcast: Podcast) =
    PodcastSummaryDto(podcast.id.value, podcast.url.value, podcast.name, podcast.image, podcast.created.toString(), podcast.updated.toString())

private fun podcastDetailDto(podcast: Podcast, episodes: List<EpisodeWithPlayback>) =
    PodcastDetailDto(
        id = podcast.id.value,
        url = podcast.url.value,
        name = podcast.name,
        image = podcast.image,
        created = podcast.created.toString(),
        updated = podcast.updated.toString(),
        episodes = episodes.map(::episodeDetailDto)
    )

private fun episodeDetailDto(ep: EpisodeWithPlayback) =
    EpisodeDetailDto(
        id = ep.episode.id.value,
        title = ep.episode.title,
        description = ep.episode.description,
        audioUrl = ep.episode.audioUrl,
        duration = ep.episode.duration?.formatted(),
        publishedAt = ep.episode.publishedAt?.toString(),
        played = ep.played,
        progressMs = ep.progressMs,
    )

private fun Duration.formatted(): String =
    toComponents { _, hours, minutes, seconds, _ ->
        if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
