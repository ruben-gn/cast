package application

import application.usecase.FindRecentUnplayedEpisodes
import application.usecase.GetEpisodeDetail
import application.usecase.GetPodcastDetail
import application.usecase.GetQueueDetail
import application.usecase.RemovePodcast
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*

fun Application.installApplicationModule() {
    dependencies {
        provide<GetEpisodeDetail> { GetEpisodeDetail(resolve(), resolve(), resolve()) }
        provide<GetPodcastDetail> { GetPodcastDetail(resolve(), resolve(), resolve(), resolve()) }
        provide<GetQueueDetail> { GetQueueDetail(resolve(), resolve(), resolve(), resolve(), resolve()) }
        provide<FindRecentUnplayedEpisodes> { FindRecentUnplayedEpisodes(resolve(), resolve(), resolve(), resolve(), resolve()) }
        provide<RemovePodcast> { RemovePodcast(resolve(), resolve(), resolve(), resolve()) }
    }
}
