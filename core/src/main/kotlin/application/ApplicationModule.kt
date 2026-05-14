package application

import application.usecase.GetPodcastDetail
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import playback.core.usecase.GetPlaybackStates
import podcast.core.usecase.GetPodcast
import podcast.core.usecase.ListEpisodes

fun Application.installApplicationModule() {
    dependencies {
        provide<GetPodcastDetail> { GetPodcastDetail(resolve<GetPodcast>(), resolve<ListEpisodes>(), resolve<GetPlaybackStates>()) }
    }
}
