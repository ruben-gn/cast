package podcast

import configuration.ConnectionProvider
import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import podcast.adapters.api.podcastApi
import podcast.adapters.persistence.SQLitePodcastCatalog
import podcast.adapters.rss.RssFeedInfoProvider
import podcast.core.ports.FeedInfoProvider
import podcast.core.ports.PodcastCatalog
import podcast.core.usecase.AddFeed
import podcast.core.usecase.GetPodcast
import podcast.core.usecase.ListEpisodes
import podcast.core.usecase.ListPodcasts
import podcast.core.usecase.UpdateFeed
import podcast.core.usecase.UpdateFeeds
import kotlin.time.Duration.Companion.minutes

fun Application.installPodcastModule(
    podcastCatalog: PodcastCatalog? = null
) {
    dependencies {
        provide<PodcastCatalog> { podcastCatalog ?: SQLitePodcastCatalog(resolve<ConnectionProvider>()) }
        provide<FeedInfoProvider> { RssFeedInfoProvider(resolve()) }

        provide<ListPodcasts> { ListPodcasts(resolve()) }
        provide<GetPodcast> { GetPodcast(resolve()) }

        provide<ListEpisodes> { ListEpisodes(resolve()) }

        provide<UpdateFeed> { UpdateFeed(resolve(), resolve(), resolve()) }
        provide<UpdateFeeds> { UpdateFeeds(resolve(), resolve()) }

        provide<AddFeed> { AddFeed(resolve(), resolve(), resolve(), resolve()) }
    }

    routing {
        route("/api/podcasts") { podcastApi(dependencies) }
    }

    launch {
        val updateFeeds = dependencies.resolve<UpdateFeeds>()
        while(isActive) {
            updateFeeds()
            delay(5.minutes)
        }
    }
}
