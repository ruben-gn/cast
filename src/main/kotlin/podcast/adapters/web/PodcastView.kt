package podcast.adapters.web

import io.ktor.server.html.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import kotlinx.html.body
import kotlinx.html.h3
import kotlinx.html.p
import podcast.core.usecase.ListPodcasts

fun Route.podcastView(dependencies: DependencyRegistry) {

    val listPodcasts: ListPodcasts by dependencies

    route("podcasts") {
        get {
            val podcasts = listPodcasts()
            call.respondHtml {
                body {
                    podcasts.forEach { podcast ->
                        h3 { +podcast.name }
                        p { +podcast.url }
                    }
                }
            }
        }
    }
}