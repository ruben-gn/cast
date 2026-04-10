package podcast

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import podcast.core.port.PodcastPersistence
import podcast.core.usecase.AddPodcast
import podcast.core.usecase.ListPodcasts
import podcast.infrastructure.InMemoryPodcastPersistence
import podcast.infrastructure.web.podcastRouting
import java.time.Clock

fun Application.podcastModule(clock: Clock = Clock.systemUTC()) {
    dependencies {
        provide<Clock> { clock }
        provide<PodcastPersistence> { InMemoryPodcastPersistence() }

        provide<ListPodcasts> { ListPodcasts(resolve()) }
        provide<AddPodcast> { AddPodcast(resolve(), resolve()) }
    }

    routing {
        podcastRouting(dependencies)
    }
}