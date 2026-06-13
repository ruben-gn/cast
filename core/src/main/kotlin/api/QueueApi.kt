package api

import application.usecase.GetQueueDetail
import cast.api.ReorderQueueRequest
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import podcast.core.usecase.ListPodcasts
import queue.core.usecase.AddEpisodeLast
import queue.core.usecase.DequeueEpisode
import queue.core.usecase.ReorderQueue
import shared.model.EpisodeId

fun Route.queueApi(dependencies: DependencyRegistry) {

    val getQueueDetail: GetQueueDetail by dependencies
    val dequeueEpisode: DequeueEpisode by dependencies
    val addEpisodeLast: AddEpisodeLast by dependencies
    val reorderQueue: ReorderQueue by dependencies
    val listPodcasts: ListPodcasts by dependencies

    suspend fun queueResponse(): List<cast.api.EpisodeDetailDto> {
        val episodes = getQueueDetail()
        val podcasts = listPodcasts().associateBy { it.id }
        return episodes.map { ep ->
            val podcast = podcasts[ep.episode.podcastId]
            episodeDetailDto(ep, podcastId = ep.episode.podcastId.value, podcastName = podcast?.name, podcastImage = podcast?.image)
        }
    }

    get {
        call.respond(queueResponse())
    }

    put {
        val body = call.receive<ReorderQueueRequest>()
        reorderQueue(body.episodeIds.map(::EpisodeId))
        call.respond(queueResponse())
    }

    post("/{episodeId}") {
        val episodeId = call.parameters.getOrFail("episodeId").let(::EpisodeId)
        addEpisodeLast(episodeId)
        call.respond(queueResponse())
    }

    delete("/{episodeId}") {
        val episodeId = call.parameters.getOrFail("episodeId").let(::EpisodeId)
        dequeueEpisode(episodeId)
        call.respond(queueResponse())
    }
}
