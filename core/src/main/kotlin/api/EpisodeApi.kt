package api

import application.usecase.FindRecentUnplayedEpisodes
import application.usecase.GetEpisodeDetail
import application.usecase.MarkEpisodePlayed
import application.usecase.MarkEpisodeUnplayed
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import shared.model.EpisodeId

fun Route.episodeApi(dependencies: DependencyRegistry) {
    val findRecentUnplayedEpisodes: FindRecentUnplayedEpisodes by dependencies
    val getEpisodeDetail: GetEpisodeDetail by dependencies
    val markEpisodePlayed: MarkEpisodePlayed by dependencies
    val markEpisodeUnplayed: MarkEpisodeUnplayed by dependencies

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
        val found = markEpisodePlayed(episodeId)
        call.respond(if (found) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }

    delete("{episodeId}/played") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        val found = markEpisodeUnplayed(episodeId)
        call.respond(if (found) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }
}
