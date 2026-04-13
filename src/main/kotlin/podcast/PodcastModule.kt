package podcast

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import podcast.core.port.FeedInfoProvider
import podcast.core.port.PodcastPersistence
import podcast.core.usecase.AddFeed
import podcast.core.usecase.ListPodcasts
import podcast.infrastructure.InMemoryPodcastPersistence
import podcast.infrastructure.RssFeedInfoProvider
import podcast.infrastructure.web.podcastRouting
import java.time.Clock

fun Application.installPodcastModule(clock: Clock = Clock.systemUTC()) {
    dependencies {
        provide<Clock> { clock }

        provide<PodcastPersistence> { InMemoryPodcastPersistence() }
        provide<FeedInfoProvider> { RssFeedInfoProvider(resolve()) }

        provide<ListPodcasts> { ListPodcasts(resolve()) }
        provide<AddFeed> { AddFeed(resolve(), resolve(), resolve()) }
    }

    routing {
        podcastRouting(dependencies)
    }
}