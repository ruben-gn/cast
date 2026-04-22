package podcast

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import podcast.adapters.RssFeedInfoProvider
import podcast.adapters.persistence.SQLitePodcastPersistence
import podcast.adapters.web.api.podcastApi
import podcast.adapters.web.view.podcastView
import podcast.core.AddFeed
import podcast.core.GetPodcast
import podcast.core.ListPodcasts
import podcast.core.port.FeedInfoProvider
import podcast.core.port.PodcastPersistence
import java.sql.Connection
import java.time.Clock


fun Application.installPodcastModule(
    clock: Clock = Clock.systemUTC()
) {
    dependencies {
        provide<Clock> { clock }

        provide<PodcastPersistence> {
            try {
                resolve<PodcastPersistence>()
            } catch (_: Exception) {
                SQLitePodcastPersistence(resolve<Connection>())
            }
        }
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
