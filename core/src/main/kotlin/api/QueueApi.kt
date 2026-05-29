package api

import application.usecase.GetQueueDetail
import cast.api.ReorderQueueRequest
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import queue.core.usecase.AddEpisodeLast
import queue.core.usecase.DequeueEpisode
import queue.core.usecase.ReorderQueue
import shared.model.EpisodeId

fun Route.queueApi(dependencies: DependencyRegistry) {

    val getQueueDetail: GetQueueDetail by dependencies
    val dequeueEpisode: DequeueEpisode by dependencies
    val addEpisodeLast: AddEpisodeLast by dependencies
    val reorderQueue: ReorderQueue by dependencies

    get {
        call.respond(getQueueDetail().map(::episodeDetailDto))
    }

    put {
        val body = call.receive<ReorderQueueRequest>()
        reorderQueue(body.episodeIds.map(::EpisodeId))
        call.respond(getQueueDetail().map(::episodeDetailDto))
    }

    post("/{episodeId}") {
        val episodeId = call.parameters.getOrFail("episodeId").let(::EpisodeId)
        addEpisodeLast(episodeId)
        call.respond(getQueueDetail().map(::episodeDetailDto))
    }

    delete("/{episodeId}") {
        val episodeId = call.parameters.getOrFail("episodeId").let(::EpisodeId)
        dequeueEpisode(episodeId)
        call.respond(getQueueDetail().map(::episodeDetailDto))
    }
}
