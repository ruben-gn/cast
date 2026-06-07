package api

import application.model.EpisodeWithPlayback
import application.usecase.FindRecentUnplayedEpisodes
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.GetPlaybackStates
import playback.core.usecase.MarkPlayed
import playback.core.usecase.MarkUnplayed
import podcast.core.usecase.FindEpisode
import podcast.core.usecase.GetPodcast
import podcast.core.usecase.ListPodcasts
import settings.core.usecase.GetSettings
import shared.model.EpisodeId

fun Route.episodeApi(dependencies: DependencyRegistry) {
    val findEpisode: FindEpisode by dependencies
    val findRecentUnplayedEpisodes: FindRecentUnplayedEpisodes by dependencies
    val getPodcast: GetPodcast by dependencies
    val getPlaybackState: GetPlaybackState by dependencies
    val getPlaybackStates: GetPlaybackStates by dependencies
    val getSettings: GetSettings by dependencies
    val listPodcasts: ListPodcasts by dependencies
    val markPlayed: MarkPlayed by dependencies
    val markUnplayed: MarkUnplayed by dependencies

    get("{episodeId}") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        val episode = findEpisode(episodeId) ?: return@get call.respond(HttpStatusCode.NotFound)
        val playback = getPlaybackState(episodeId)
        val podcast = getPodcast(episode.podcastId)
        call.respond(episodeDetailDto(
            EpisodeWithPlayback(episode, playback.progressMs, playback.played),
            podcastId = episode.podcastId.value,
            podcastName = podcast?.name,
            podcastImage = podcast?.image,
        ))
    }

    get("recent") {
        val episodes = findRecentUnplayedEpisodes()
        val podcasts = listPodcasts().associateBy { it.id }
        val settings = getSettings()
        val filtered = if (settings.recentListeningOnly) {
            episodes.filter { podcasts[it.podcastId]?.listening == true }
        } else {
            episodes
        }
        val states = getPlaybackStates(filtered.map { it.id })
        call.respond(filtered.map { ep ->
            val podcast = podcasts[ep.podcastId]
            val state = states[ep.id]
            episodeDetailDto(
                EpisodeWithPlayback(ep, state?.progressMs ?: 0, state?.played ?: false),
                podcastId = ep.podcastId.value,
                podcastName = podcast?.name,
                podcastImage = podcast?.image,
            )
        })
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
