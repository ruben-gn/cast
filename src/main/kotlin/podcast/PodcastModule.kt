package grootnibbel.ink.podcast

import grootnibbel.ink.podcast.core.PodcastApi
import grootnibbel.ink.podcast.core.PodcastPersistence
import grootnibbel.ink.podcast.infrastructure.InMemoryPodcastPersistence
import grootnibbel.ink.podcast.infrastructure.html.podcastRouting
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.di.dependencies
import io.ktor.server.routing.*

fun Application.podcastModule() {
    install(ContentNegotiation) {
        json()
    }

    dependencies {
        provide<PodcastPersistence> { InMemoryPodcastPersistence() }
        provide<PodcastApi> { PodcastApi(resolve()) }
    }

    routing {
        podcastRouting(dependencies)
    }
}