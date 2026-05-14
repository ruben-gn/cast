package application

import application.usecase.GetPodcastDetail
import io.ktor.server.application.*
import io.ktor.server.plugins.di.*

fun Application.installApplicationModule() {
    dependencies {
        provide<GetPodcastDetail> { GetPodcastDetail(resolve(), resolve(), resolve()) }
    }
}
