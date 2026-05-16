package api

import application.model.EpisodeWithPlayback
import application.usecase.FindRecentUnplayedEpisodes
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import playback.core.usecase.MarkPlayed
import playback.core.usecase.MarkUnplayed
import podcast.core.usecase.FindEpisode
import shared.model.EpisodeId

fun Route.episodeApi(dependencies: DependencyRegistry) {
    val findEpisode: FindEpisode by dependencies
    val findRecentUnplayedEpisodes: FindRecentUnplayedEpisodes by dependencies
    val markPlayed: MarkPlayed by dependencies
    val markUnplayed: MarkUnplayed by dependencies

    get("recent") {
        call.respond(findRecentUnplayedEpisodes().map { episodeDetailDto(EpisodeWithPlayback(it, 0, false)) })
    }

    post("{episodeId}/played") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        findEpisode(episodeId) ?: return@post call.respond(HttpStatusCode.NotFound)
        markPlayed(episodeId)
        call.respond(HttpStatusCode.NoContent)
    }

    delete("{episodeId}/played") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        findEpisode(episodeId) ?: return@delete call.respond(HttpStatusCode.NotFound)
        markUnplayed(episodeId)
        call.respond(HttpStatusCode.NoContent)
    }
}
