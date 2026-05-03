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
    div {
        id = "add-feed-modal"
        div(classes = "modal-content") {
            span(classes = "close-modal") {
                attributes["onclick"] = "document.getElementById('add-feed-modal').classList.remove('open')"
                unsafe { +"&times;" }
            }
            h3(classes = "modal-title") { +"Add New Podcast" }
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
        attributes["hx-post"] = "/podcasts"
        attributes["hx-target"] = "#content-container"
        attributes["hx-swap"] = "outerHTML"
        attributes["hx-indicator"] = "#sub-spinner"
        attributes["hx-on::after-request"] = "if(event.detail.successful) document.getElementById('add-feed-modal').classList.remove('open')"
        classes = setOf("subscribe-form")

        input(type = InputType.url, name = "url") {
            placeholder = "Enter RSS feed URL..."
            required = true
            classes = setOf("url-input")
        }
        div(classes = "form-actions") {
            button(type = ButtonType.submit, classes = "subscribe-btn") {
                +"Subscribe"
            }
            span(classes = "htmx-indicator spinner-text") {
                id = "sub-spinner"
                +"Processing..."
            }
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
    div(classes = "episode-item") {

        button(classes = "episode-play-btn") {
            attributes["data-id"] = episode.id.value
            attributes["data-audio-url"] = episode.audioUrl
            attributes["data-title"] = episode.title
            attributes["onclick"] = "playEpisode(this.dataset.id, this.dataset.audioUrl, this.dataset.title)"
            attributes["title"] = "Play ${episode.title}"
            unsafe { +"&#9654;" }
        }

        val toggleId = "tgl-${episode.id.value}"
        input(type = InputType.checkBox) {
            id = toggleId
            classes = setOf("episode-toggle")
        }

        label {
            htmlFor = toggleId
            classes = setOf("episode-header")

            div(classes = "episode-row") {
                span(classes = "episode-title") { +episode.title }
                div(classes = "episode-extras") {
                    episode.duration?.let { duration ->
                        span(classes = "episode-duration") { +duration.formatted() }
                    }
                }
            }
        }

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
