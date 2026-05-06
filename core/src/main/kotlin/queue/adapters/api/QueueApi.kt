package queue.adapters.api

import cast.api.QueueDto
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import queue.core.model.Queue
import queue.core.usecase.DequeueEpisode
import queue.core.usecase.AddEpisodeAt
import queue.core.usecase.AddEpisodeFirst
import queue.core.usecase.AddEpisodeLast
import queue.core.usecase.GetQueue
import shared.model.EpisodeId

fun Route.queueApi(dependencies: DependencyRegistry) {

    val getQueue: GetQueue by dependencies
    val dequeueEpisode: DequeueEpisode by dependencies
    val addEpisodeFirst: AddEpisodeFirst by dependencies
    val addEpisodeLast: AddEpisodeLast by dependencies
    val addEpisodeAt: AddEpisodeAt by dependencies

    get {
        call.respondQueue(getQueue())
    }

    delete("/{episodeId}") {
        val episodeId = call.parameters.getOrFail("episodeId").let(::EpisodeId)

        call.respondQueue(dequeueEpisode(episodeId))
    }

    post("/{episodeId}/first") {
        val episodeId = call.parameters.getOrFail("episodeId").let(::EpisodeId)
        call.respondQueue(addEpisodeFirst(episodeId))
    }

    post("/{episodeId}/last") {
        val episodeId = call.parameters.getOrFail("episodeId").let(::EpisodeId)
        call.respondQueue(addEpisodeLast(episodeId))
    }

    post("/{episodeId}/{position}") {
        val episodeId = call.parameters.getOrFail("episodeId").let(::EpisodeId)
        val position = call.parameters.getOrFail("position").toInt()

        call.respondQueue(addEpisodeAt(episodeId, position))
    }

}

private suspend fun RoutingCall.respondQueue(queue: Queue) = respond(queueDto(queue))

private fun queueDto(queue: Queue) = QueueDto(episodeIds = queue.episodeIds.map(EpisodeId::value))