package api

import application.usecase.GetQueueDetail
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.util.*
import queue.core.usecase.AddEpisodeLast
import queue.core.usecase.DequeueEpisode
import shared.model.EpisodeId

fun Route.queueApi(dependencies: DependencyRegistry) {

    val getQueueDetail: GetQueueDetail by dependencies
    val dequeueEpisode: DequeueEpisode by dependencies
    val addEpisodeLast: AddEpisodeLast by dependencies

    get {
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
