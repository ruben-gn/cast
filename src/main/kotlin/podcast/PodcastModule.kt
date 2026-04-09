package podcast

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import podcast.core.port.PodcastPersistence
import podcast.core.usecase.AddPodcast
import podcast.core.usecase.ListPodcasts
import podcast.infrastructure.InMemoryPodcastPersistence
import podcast.infrastructure.web.podcastRouting

fun Application.podcastModule() {
    install(ContentNegotiation) {
        json()
    }

    dependencies {
        provide<PodcastPersistence> { InMemoryPodcastPersistence() }

        provide<ListPodcasts> { ListPodcasts(resolve()) }
        provide<AddPodcast> { AddPodcast(resolve()) }
    }

    routing {
        podcastRouting(dependencies)
    }
}