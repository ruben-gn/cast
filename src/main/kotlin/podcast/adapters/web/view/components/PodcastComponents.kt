package podcast.adapters.web.view.components

import kotlinx.html.*
import podcast.core.model.Episode
import podcast.core.model.Podcast

fun FlowContent.podcastList(podcasts: List<Podcast>) {
    div {
        id = "content-container"
        style = "display: grid; grid-template-columns: repeat(auto-fill, 200px); justify-content: center; max-width: 1100px; gap: 7px; margin: 0 auto;"

        podcasts.forEach { podcastCard(it) }
    }
}

private fun FlowContent.podcastCard(podcast: Podcast) {
    div {
        attributes["hx-get"] = "/podcasts/${podcast.id}"
        attributes["hx-target"] = "#content-container"
        attributes["hx-swap"] = "outerHTML"
        attributes["hx-push-url"] = "true"
        style = "text-decoration: none; color: inherit; cursor: pointer;"
        div {
            style = "width: 200px; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.05); transition: transform 0.2s; cursor: pointer;"
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

fun FlowContent.podcastDetails(podcast: Podcast) {
    div {
        id = "content-container"
        style = "max-width: 800px; margin: 0 auto;"

        a {
            attributes["hx-get"] = "/podcasts"
            attributes["hx-target"] = "#content-container"
            attributes["hx-swap"] = "outerHTML"
            attributes["hx-push-url"] = "true"
            style = "display: inline-block; margin-bottom: 24px; font-size: 14px; color: #666; text-decoration: none; cursor: pointer;"
            +"← All podcasts"
        }

        div {
            style = "display: flex; gap: 24px; align-items: flex-start; margin-bottom: 40px;"
            img(src = podcast.image, alt = podcast.name) {
                style = "width: 160px; height: 160px; object-fit: cover; border-radius: 12px; flex-shrink: 0;"
            }
            div {
                h1 { style = "margin: 0 0 8px; font-size: 24px;"; +podcast.name }
                p { style = "margin: 0; font-size: 13px; color: #888;"; +"${podcast.episodes.size} episodes" }
            }
        }

        if (podcast.episodes.isEmpty()) {
            p { style = "color: #888; font-size: 14px;"; +"No episodes available." }
        } else {
            podcast.episodes.forEach { episodeItem(it) }
        }

        script {
            unsafe {
                raw("initToggles();")
            }
        }
    }
}

private fun FlowContent.episodeItem(episode: Episode) {
    div {
        style = "background: white; border-radius: 12px; padding: 20px; margin-bottom: 12px; box-shadow: 0 2px 4px rgba(0,0,0,0.05);"
        attributes["onclick"] = "toggleDescription('${episode.id}')"

        div {
            style = "display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8px;"
            span { style = "font-size: 15px; font-weight: 700;"; +episode.title }
            div {
                style = "display: flex; align-items: center; gap: 10px; flex-shrink: 0; margin-left: 12px;"
                episode.duration?.let { duration ->
                    span { style = "font-size: 12px; color: #888; white-space: nowrap;"; +formatDuration(duration) }
                }
                span {
                    id = "btn-${episode.id}"
                    style = "color: #aaa; font-size: 14px; display: inline-block; transition: transform 0.3s ease; cursor: pointer;"
                    +"▼"
                }
            }
        }
        div {
            style = "position: relative;"
            div {
                attributes["id"] = "desc-${episode.id}"
                attributes["data-expanded"] = "0"
                style = "max-height: 6em; overflow: hidden; font-size: 14px; line-height: 1.5; color: #666; transition: max-height 0.35s ease;"
                unsafe { raw(episode.description) }
            }
            div {
                attributes["id"] = "fade-${episode.id}"
                style = "position: absolute; bottom: 0; left: 0; right: 0; height: 3em; background: linear-gradient(transparent, white); pointer-events: none; transition: opacity 0.35s ease;"
            }
        }
    }
}