package podcast

import configuration.ConnectionProvider
import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import podcast.adapters.persistence.SQLitePodcastCatalog
import podcast.adapters.rss.RssFeedInfoProvider
import podcast.adapters.web.api.podcastApi
import podcast.adapters.web.view.podcastView
import podcast.core.AddFeed
import podcast.core.GetPodcast
import podcast.core.ListEpisodes
import podcast.core.ListPodcasts
import podcast.core.ports.FeedInfoProvider
import podcast.core.ports.PodcastCatalog
import java.time.Clock

const val PODCAST_ROUTE = "podcasts"

fun Application.installPodcastModule(
    podcastCatalog: PodcastCatalog? = null
) {
    dependencies {
        provide<PodcastCatalog> { podcastCatalog ?: SQLitePodcastCatalog(resolve<ConnectionProvider>()) }
        provide<FeedInfoProvider> { RssFeedInfoProvider(resolve()) }

        provide<ListPodcasts> { ListPodcasts(resolve()) }
        provide<GetPodcast> { GetPodcast(resolve()) }
        provide<ListEpisodes> { ListEpisodes(resolve()) }
        provide<AddFeed> { AddFeed(resolve(), resolve(), resolve()) }
    }

    routing {
        route("/api") { podcastApi(dependencies) }
        podcastView(dependencies)
    }
}
