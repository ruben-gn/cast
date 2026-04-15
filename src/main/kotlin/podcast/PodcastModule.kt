package podcast

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import podcast.adapters.InMemoryPodcastPersistence
import podcast.adapters.RssFeedInfoProvider
import podcast.adapters.web.podcastApi
import podcast.adapters.web.podcastView
import podcast.core.port.FeedInfoProvider
import podcast.core.port.PodcastPersistence
import podcast.core.AddFeed
import podcast.core.GetPodcast
import podcast.core.ListPodcasts
import java.time.Clock


fun Application.installPodcastModule(
    clock: Clock = Clock.systemUTC(),
    persistence: PodcastPersistence = InMemoryPodcastPersistence()
) {
    dependencies {
        provide<Clock> { clock }

        provide<PodcastPersistence> { persistence }
        provide<FeedInfoProvider> { RssFeedInfoProvider(resolve()) }

        provide<ListPodcasts> { ListPodcasts(resolve()) }
        provide<GetPodcast> { GetPodcast(resolve()) }
        provide<AddFeed> { AddFeed(resolve(), resolve(), resolve()) }
    }

    routing {
        route("/api") {
            podcastApi(dependencies)
        }
        podcastView(dependencies)
    }
}
