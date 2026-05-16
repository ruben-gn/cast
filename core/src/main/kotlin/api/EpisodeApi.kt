package api

import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import playback.core.usecase.MarkPlayed
import playback.core.usecase.MarkUnplayed
import podcast.core.ports.PodcastCatalog
import shared.model.EpisodeId

fun Route.episodeApi(dependencies: DependencyRegistry) {
    val catalog: PodcastCatalog by dependencies
    val markPlayed: MarkPlayed by dependencies
    val markUnplayed: MarkUnplayed by dependencies

    post("{episodeId}/played") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        catalog.findEpisodeById(episodeId) ?: return@post call.respond(HttpStatusCode.NotFound)
        markPlayed(episodeId)
        call.respond(HttpStatusCode.NoContent)
    }

    delete("{episodeId}/played") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        catalog.findEpisodeById(episodeId) ?: return@delete call.respond(HttpStatusCode.NotFound)
        markUnplayed(episodeId)
        call.respond(HttpStatusCode.NoContent)
    }
}
