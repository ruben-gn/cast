package api

import application.usecase.FindRecentUnplayedEpisodes
import application.usecase.GetEpisodeDetail
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
    val getEpisodeDetail: GetEpisodeDetail by dependencies
    val markPlayed: MarkPlayed by dependencies
    val markUnplayed: MarkUnplayed by dependencies

    get("{episodeId}") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        val episode = getEpisodeDetail(episodeId) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(episodeDetailDto(episode))
    }

    get("recent") {
        call.respond(findRecentUnplayedEpisodes().map(::episodeDetailDto))
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
