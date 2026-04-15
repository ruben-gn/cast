package podcast.adapters.web

import io.ktor.http.*
import io.ktor.server.html.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.*
import podcast.core.GetPodcast
import podcast.core.ListPodcasts

fun Route.podcastView(dependencies: DependencyRegistry) {

    val listPodcasts: ListPodcasts by dependencies
    val getPodcast: GetPodcast by dependencies

    route("podcasts") {
        get {
            val podcasts = listPodcasts()
            call.respondHtml {
                head {
                    montserratFont()
                    title { +"Cast" }
                }
                body {
                    style = "font-family: 'Montserrat', sans-serif; background-color: #e4e4e4; margin: 0; padding: 40px; color: #333;"

                    div {
                        style =
                            "display: grid; grid-template-columns: repeat(auto-fill, 200px); justify-content: center; max-width: 1100px; gap: 7px; margin: 0 auto;"

                        podcasts.forEach { podcast ->
                            a(href = "/podcasts/${podcast.id}") {
                                style = "text-decoration: none; color: inherit;"
                                div {
                                    style =
                                        "width: 200px; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.05); transition: transform 0.2s; cursor: pointer;"

                                    img(src = podcast.image, alt = podcast.name) {
                                        style = "width: 100%; height: 200px; object-fit: cover; display: block;"
                                    }

                                    div {
                                        style = "padding: 12px; text-align: left;"
                                        p {
                                            style =
                                                "margin: 0; font-size: 14px; line-height: 1.4; height: 40px; overflow: hidden; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical;"
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

        get("{id}") {
            val id = call.parameters["id"]!!
            val podcast = getPodcast(id) ?: return@get call.respond(HttpStatusCode.NotFound, "Podcast not found")

            call.respondHtml {
                head {
                    montserratFont()
                    title { +podcast.name }
                }
                body {
                    style = "font-family: 'Montserrat', sans-serif; background-color: #e4e4e4; margin: 0; padding: 40px; color: #333;"

                    script {
                        unsafe {
                            raw("""
                                document.addEventListener('DOMContentLoaded', function() {
                                  document.querySelectorAll('[id^="desc-"]').forEach(function(d) {
                                    var id = d.id.slice(5);
                                    if (d.scrollHeight <= d.clientHeight) {
                                      d.style.maxHeight = 'none';
                                      var f = document.getElementById('fade-' + id);
                                      var b = document.getElementById('btn-' + id);
                                      if (f) f.style.display = 'none';
                                      if (b) b.style.display = 'none';
                                    }
                                  });
                                });
                                function toggleDesc(id) {
                                  var d = document.getElementById('desc-' + id);
                                  var f = document.getElementById('fade-' + id);
                                  var b = document.getElementById('btn-' + id);
                                  var expanded = d.dataset.expanded === '1';
                                  if (expanded) {
                                    d.style.maxHeight = '6em';
                                    f.style.opacity = '1';
                                    b.style.transform = 'rotate(0deg)';
                                    d.dataset.expanded = '0';
                                  } else {
                                    d.style.maxHeight = d.scrollHeight + 'px';
                                    f.style.opacity = '0';
                                    b.style.transform = 'rotate(180deg)';
                                    d.dataset.expanded = '1';
                                  }
                                }
                            """.trimIndent())
                        }
                    }

                    div {
                        style = "max-width: 800px; margin: 0 auto;"

                        a(href = "/podcasts") {
                            style = "display: inline-block; margin-bottom: 24px; font-size: 14px; color: #666; text-decoration: none;"
                            +"← All podcasts"
                        }

                        div {
                            style = "display: flex; gap: 24px; align-items: flex-start; margin-bottom: 40px;"

                            img(src = podcast.image, alt = podcast.name) {
                                style = "width: 160px; height: 160px; object-fit: cover; border-radius: 12px; flex-shrink: 0;"
                            }

                            div {
                                h1 {
                                    style = "margin: 0 0 8px; font-size: 24px;"
                                    +podcast.name
                                }
                                p {
                                    style = "margin: 0; font-size: 13px; color: #888;"
                                    +"${podcast.episodes.size} episodes"
                                }
                            }
                        }

                        if (podcast.episodes.isEmpty()) {
                            p {
                                style = "color: #888; font-size: 14px;"
                                +"No episodes available."
                            }
                        } else {
                            podcast.episodes.forEach { episode ->
                                div {
                                    style =
                                        "background: white; border-radius: 12px; padding: 20px; margin-bottom: 12px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); cursor: pointer;"
                                    attributes["onclick"] = "toggleDesc('${episode.id}')"

                                    div {
                                        style = "display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8px;"
                                        span {
                                            style = "font-size: 15px; font-weight: 700;"
                                            +episode.title
                                        }
                                        div {
                                            style = "display: flex; align-items: center; gap: 10px; flex-shrink: 0; margin-left: 12px;"
                                            episode.duration?.let { duration ->
                                                span {
                                                    style = "font-size: 12px; color: #888; white-space: nowrap;"
                                                    +formatDuration(duration)
                                                }
                                            }
                                            span {
                                                attributes["id"] = "btn-${episode.id}"
                                                style = "color: #aaa; font-size: 14px; display: inline-block; transition: transform 0.3s ease;"
                                                +"▼"
                                            }
                                        }
                                    }
                                    episode.description.let { description ->
                                        div {
                                            style = "position: relative;"
                                            div {
                                                attributes["id"] = "desc-${episode.id}"
                                                attributes["data-expanded"] = "0"
                                                style = "max-height: 6em; overflow: hidden; font-size: 14px; line-height: 1.5; color: #666; transition: max-height 0.35s ease;"
                                                unsafe { raw(description) }
                                            }
                                            div {
                                                attributes["id"] = "fade-${episode.id}"
                                                style = "position: absolute; bottom: 0; left: 0; right: 0; height: 3em; background: linear-gradient(transparent, white); pointer-events: none; transition: opacity 0.35s ease;"
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
    }
}

private fun formatDuration(duration: String): String {
    val parts = duration.split(":")
    val totalSeconds = when (parts.size) {
        1 -> parts[0].toLongOrNull() ?: return duration
        2 -> (parts[0].toLongOrNull() ?: return duration) * 60 + (parts[1].toLongOrNull() ?: return duration)
        3 -> (parts[0].toLongOrNull() ?: return duration) * 3600 +
                (parts[1].toLongOrNull() ?: return duration) * 60 +
                (parts[2].toLongOrNull() ?: return duration)
        else -> return duration
    }
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes} min"
}

private fun HEAD.montserratFont() {
    link(rel = "preconnect", href = "https://fonts.googleapis.com")
    link(rel = "preconnect", href = "https://fonts.gstatic.com") {
        attributes["crossorigin"] = "anonymous"
    }
    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;700&display=swap")
}
