package podcast.adapters.web.view.components

import kotlinx.html.*
import podcast.core.model.Episode
import podcast.core.model.Podcast

fun FlowContent.podcastList(podcasts: List<Podcast>) {
    div {
        style = "max-width: 1100px; margin: 0 auto;"
        podcastGrid(podcasts)
    }
}

fun FlowContent.addFeedModal() {
    div {
        id = "add-feed-modal"
        div(classes = "modal-content") {
            span(classes = "close-modal") {
                attributes["onclick"] = "document.getElementById('add-feed-modal').classList.remove('open')"
                unsafe { +"&times;" }
            }
            h3 { style = "margin-top: 0;"; +"Add New Podcast" }
            subscribeForm()
        }
    }
}

fun FlowContent.podcastGrid(podcasts: List<Podcast>) {
    div {
        style = "display: grid; grid-template-columns: repeat(auto-fill, 200px); justify-content: center; gap: 7px;"
        podcasts.forEach { podcastCard(it) }
    }
}

private fun FlowContent.subscribeForm() {
    form {
        attributes["hx-post"] = "/podcasts"
        attributes["hx-target"] = "#content-container"
        attributes["hx-swap"] = "outerHTML"
        attributes["hx-indicator"] = "#sub-spinner"
        attributes["hx-on::after-request"] = "if(event.detail.successful) document.getElementById('add-feed-modal').classList.remove('open')"
        style = "display: flex; flex-direction: column; gap: 15px;"

        input(type = InputType.url, name = "url") {
            placeholder = "Enter RSS feed URL..."
            required = true
            style = "padding: 12px 16px; border-radius: 8px; border: 1px solid #ccc; font-family: inherit; font-size: 14px;"
        }
        div {
            style = "display: flex; align-items: center; gap: 15px;"
            button(type = ButtonType.submit) {
                style = "padding: 12px 24px; border-radius: 8px; border: none; background: #333; color: white; font-weight: bold; cursor: pointer; font-family: inherit; flex-grow: 1;"
                +"Subscribe"
            }
            span("htmx-indicator") {
                id = "sub-spinner"
                style = "font-size: 12px; color: #666;"
                +"Processing..."
            }
        }
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
            classes = setOf("podcast-card")
            style =
                "width: 200px; background: white; border-radius: 12px; overflow: hidden; box-shadow: 0 4px 6px rgba(0,0,0,0.05); transition: transform 0.2s, box-shadow 0.2s; cursor: pointer;"
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

fun FlowContent.podcastDetails(podcast: Podcast, episodes: List<Episode>) {
    div {
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
                p { style = "margin: 0; font-size: 13px; color: #888;"; +"${episodes.size} episodes" }
            }
        }

        if (episodes.isEmpty()) {
            p { style = "color: #888; font-size: 14px;"; +"No episodes available." }
        } else {
            episodes.forEach { episodeItem(it) }
        }
    }
}

private fun FlowContent.episodeItem(episode: Episode) {
    div {
        style = "background: white; border-radius: 12px; padding: 20px; margin-bottom: 12px; box-shadow: 0 2px 4px rgba(0,0,0,0.05); position: relative;"

        val toggleId = "tgl-${episode.id}"
        input(type = InputType.checkBox) {
            id = toggleId
            classes = setOf("episode-toggle")
        }

        label {
            htmlFor = toggleId
            style = "display: block; cursor: pointer;"
            classes = setOf("episode-header")

            div {
                style = "display: flex; justify-content: space-between; align-items: baseline; margin-bottom: 8px;"
                span { style = "font-size: 15px; font-weight: 700;"; +episode.title }
                div {
                    style = "display: flex; align-items: center; gap: 10px; flex-shrink: 0; margin-left: 12px;"
                    episode.duration?.let { duration ->
                        span { style = "font-size: 12px; color: #888; white-space: nowrap;"; +formatDuration(duration) }
                    }
                    span("toggle-icon") { +"▼" }
                }
            }
        }

        div("description-container") {
            unsafe { raw(episode.description) }
            div("description-fade") {}
        }
    }
}
