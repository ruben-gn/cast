package podcast

import configuration.DatabaseContext
import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*
import podcast.adapters.persistence.SQLiteEpisodePersistence
import podcast.adapters.persistence.SQLitePodcastPersistence
import podcast.adapters.rss.RssFeedInfoProvider
import podcast.adapters.web.api.podcastApi
import podcast.adapters.web.view.podcastView
import podcast.core.AddFeed
import podcast.core.GetPodcast
import podcast.core.ListEpisodes
import podcast.core.ListPodcasts
import podcast.core.port.EpisodePersistence
import podcast.core.port.FeedInfoProvider
import podcast.core.port.PodcastPersistence
import java.time.Clock


fun Application.installPodcastModule(
    clock: Clock = Clock.systemUTC(),
    podcastPersistence: PodcastPersistence? = null,
    episodePersistence: EpisodePersistence? = null
) {
    dependencies {
        provide<Clock> { clock }

        provide<PodcastPersistence> {
            podcastPersistence ?: SQLitePodcastPersistence(resolve<DatabaseContext>())
        }
        provide<EpisodePersistence> {
            episodePersistence ?: SQLiteEpisodePersistence(resolve<DatabaseContext>())
        }
        provide<FeedInfoProvider> { RssFeedInfoProvider(resolve()) }

        provide<ListPodcasts> { ListPodcasts(resolve()) }
        provide<GetPodcast> { GetPodcast(resolve()) }
        provide<ListEpisodes> { ListEpisodes(resolve()) }
        provide<AddFeed> { AddFeed(resolve(), resolve(), resolve(), resolve()) }
    }

    routing {
        route("/api") {
            podcastApi(dependencies)
        }
        podcastView(dependencies)
    }
}
