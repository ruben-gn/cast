package podcast.adapters.web

import io.ktor.server.html.*
import io.ktor.server.plugins.di.*
import io.ktor.server.routing.*
import kotlinx.html.*
import podcast.core.usecase.ListPodcasts

fun Route.podcastView(dependencies: DependencyRegistry) {

    val listPodcasts: ListPodcasts by dependencies

    route("podcasts") {
        get {
            val podcasts = listPodcasts()
            call.respondHtml {
                head {
                    link(rel = "preconnect", href = "https://fonts.googleapis.com")
                    link(rel = "preconnect", href = "https://fonts.gstatic.com") {
                        attributes["crossorigin"] = "anonymous"
                    }
                    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;700&display=swap")
                }
                body {
                    style = "font-family: 'Montserrat', sans-serif; background-color: #e4e4e4; margin: 0; padding: 40px; color: #333;"

                    div {
                        style = "display: grid; grid-template-columns: repeat(auto-fill, 200px); justify-content: center; max-width: 1100px; gap: 7px; margin: 0 auto;"
                        
                        podcasts.forEach { podcast ->
                            div {
                                style = "width: 200px; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.05); transition: transform 0.2s;"
                                
                                img(src = podcast.image, alt = podcast.name) {
                                    style = "width: 100%; height: 200px; object-fit: cover; display: block;"
                                }
                                
                                div {
                                    style = "padding: 12px; text-align: left;"
                                    p { 
                                        style = "margin: 0; font-size: 14px; line-height: 1.4; height: 40px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;"
                                        +podcast.name 
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}