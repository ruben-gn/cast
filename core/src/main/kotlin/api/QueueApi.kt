package api

import application.usecase.GetQueueDetail
import cast.api.EpisodeDetailDto
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

    suspend fun queueResponse(): List<EpisodeDetailDto> =
        getQueueDetail().map(::episodeDetailDto)

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
