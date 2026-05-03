package podcast

import kotlinx.html.*
import podcast.core.models.Episode
import podcast.core.models.Podcast
import podcast.adapters.web.formatted

fun FlowContent.podcastList(podcasts: List<Podcast>) {
    div(classes = "podcast-list") {
        if (podcasts.isEmpty()) podcastEmptyState() else podcastGrid(podcasts)
    }
}

private fun FlowContent.podcastEmptyState() {
    div(classes = "empty-state") {
        div(classes = "empty-state-icon") { +"🎙" }
        h2(classes = "empty-state-title") { +"No podcasts yet" }
        p(classes = "empty-state-body") {
            +"Add your first podcast by clicking "
            strong { +"＋ Add podcast" }
            +" above."
        }
    }
}

fun FlowContent.addFeedModal() {
    dialog {
        id = "add-feed-modal"
        attributes["onclick"] = "if(event.target===this)this.close()"
        div(classes = "modal-content") {
            form {
                attributes["method"] = "dialog"
                button(classes = "close-modal") {
                    attributes["aria-label"] = "Close"
                    unsafe { +"""<svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>""" }
                }
            }
            h3(classes = "modal-title") { +"Add Podcast" }
            subscribeForm()
        }
    }
}

fun FlowContent.podcastGrid(podcasts: List<Podcast>) {
    div(classes = "podcast-grid") {
        podcasts.forEach { podcastCard(it) }
    }
}

private fun FlowContent.subscribeForm() {
    form {
        id = "modal-dismiss"
        attributes["method"] = "dialog"
    }
    form {
        attributes["hx-post"] = "/podcasts"
        attributes["hx-target"] = "#content-container"
        attributes["hx-swap"] = "outerHTML"
        attributes["hx-indicator"] = "#sub-spinner"
        attributes["hx-on::after-request"] = "handleSubResult(event)"
        attributes["hx-on::before-request"] = "document.getElementById('sub-error').classList.remove('visible')"
        classes = setOf("subscribe-form")
        noValidate = true

        label(classes = "url-label") {
            htmlFor = "rss-url-input"
            +"RSS Feed URL"
        }
        input(type = InputType.url, name = "url") {
            id = "rss-url-input"
            placeholder = "https://example.com/feed.xml"
            autoFocus = true
            classes = setOf("url-input")
        }
        p(classes = "sub-error") { id = "sub-error" }
        div(classes = "form-actions") {
            button(type = ButtonType.submit, classes = "cancel-btn") {
                attributes["form"] = "modal-dismiss"
                +"Cancel"
            }
            button(type = ButtonType.submit, classes = "subscribe-btn") {
                +"Subscribe"
            }
            span(classes = "htmx-indicator btn-spinner") { id = "sub-spinner" }
        }
    }
}

private fun FlowContent.podcastCard(podcast: Podcast) {
    div(classes = "podcast-card-link") {
        attributes["hx-get"] = "/podcasts/${podcast.id.value}"
        attributes["hx-target"] = "#content-container"
        attributes["hx-swap"] = "outerHTML"
        attributes["hx-push-url"] = "true"
        div(classes = "podcast-card") {
            img(src = podcast.image, alt = podcast.name) {
                classes = setOf("podcast-card-img")
            }
            div(classes = "podcast-card-info") {
                p(classes = "podcast-card-name") {
                    +podcast.name
                }
            }
        }
    }
}

fun FlowContent.podcastDetails(podcast: Podcast, episodes: List<Episode>) {
    div(classes = "podcast-detail") {

        a(classes = "back-link") {
            attributes["hx-get"] = "/podcasts"
            attributes["hx-target"] = "#content-container"
            attributes["hx-swap"] = "outerHTML"
            attributes["hx-push-url"] = "true"
            +"← All podcasts"
        }

        div(classes = "podcast-header") {
            img(src = podcast.image, alt = podcast.name) {
                classes = setOf("podcast-cover")
            }
            div {
                h1(classes = "podcast-title") { +podcast.name }
                p(classes = "podcast-subtitle") { +"${episodes.size} episodes" }
            }
        }

        if (episodes.isEmpty()) {
            p(classes = "empty-message") { +"No episodes available." }
        } else {
            episodes.forEach { episodeItem(it) }
        }
    }
}

private fun FlowContent.episodeItem(episode: Episode) {
    val hasDescription = episode.description.isNotBlank()
    val toggleId = "tgl-${episode.id.value}"

    div(classes = "episode-item") {

        button(classes = "episode-play-btn") {
            attributes["data-id"] = episode.id.value
            attributes["data-audio-url"] = episode.audioUrl
            attributes["data-title"] = episode.title
            attributes["onclick"] = "playEpisode(this.dataset.id, this.dataset.audioUrl, this.dataset.title)"
            attributes["title"] = "Play ${episode.title}"
            unsafe { +"""<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>""" }
        }

        if (hasDescription) {
            input(type = InputType.checkBox) {
                id = toggleId
                classes = setOf("episode-toggle")
            }
        }

        val headerRow: FlowContent.() -> Unit = {
            div(classes = "episode-row") {
                span(classes = "episode-title") { +episode.title }
                div(classes = "episode-extras") {
                    episode.duration?.let { duration ->
                        span(classes = "episode-duration") { +duration.formatted() }
                    }
                }
            }
        }

        if (hasDescription) {
            label {
                htmlFor = toggleId
                classes = setOf("episode-header")
                headerRow()
            }
        } else {
            div(classes = "episode-header episode-header--static") { headerRow() }
        }

        if (hasDescription) {
            div("description-container") {
                unsafe { raw(episode.description) }
                div("description-fade") {
                    label(classes = "show-more-btn") {
                        htmlFor = toggleId
                        +"Show more"
                    }
                }
            }

            label(classes = "show-less-btn") {
                htmlFor = toggleId
                +"Show less"
            }
        }
    }
}
