package podcast

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import kotlinx.coroutines.launch
import podcast.adapters.InMemoryPodcastPersistence
import podcast.adapters.RssFeedInfoProvider
import podcast.adapters.web.podcastApi
import podcast.adapters.web.podcastView
import podcast.core.port.FeedInfoProvider
import podcast.core.port.PodcastPersistence
import podcast.core.usecase.AddFeed
import podcast.core.usecase.ListPodcasts
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
        route("/api") {
            podcastApi(dependencies)
        }
        podcastView(dependencies)
    }
}