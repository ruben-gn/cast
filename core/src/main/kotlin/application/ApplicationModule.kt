package application

import application.usecase.FindRecentUnplayedEpisodes
import application.usecase.GetPodcastDetail
import application.usecase.GetQueueDetail
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*

fun Application.installApplicationModule() {
    dependencies {
        provide<GetPodcastDetail> { GetPodcastDetail(resolve(), resolve(), resolve(), resolve()) }
        provide<GetQueueDetail> { GetQueueDetail(resolve(), resolve(), resolve(), resolve()) }
        provide<FindRecentUnplayedEpisodes> { FindRecentUnplayedEpisodes(resolve(), resolve(), resolve()) }
    }
}
