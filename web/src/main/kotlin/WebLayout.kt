import kotlinx.html.*
import podcast.addFeedModal

fun HTML.layout(titleText: String, content: FlowContent.() -> Unit) {
    head {
        meta(name = "viewport", content = "width=device-width, initial-scale=1.0")
        montserratFont()
        link(rel = "stylesheet", href = "/static/css/style.css")
        script { src = "https://unpkg.com/htmx.org@1.9.10" }
        title { +titleText }
    }
    body {
        id = "main-body"
        header(classes = "app-header") {
            span(classes = "app-logo") { +"Cast" }
            button(classes = "header-add-btn") {
                attributes["onclick"] = "document.getElementById('add-feed-modal').classList.add('open')"
                +"＋ Add podcast"
            }
        }
        addFeedModal()
        div {
            id = "content-container"
            div(classes = "page-content") {
                content()
            }
        }
        div {
            id = "player-bar"
            div(classes = "player-info") {
                span(classes = "player-now-playing") { +"Now playing" }
                span { id = "player-title" }
            }
            audio {
                id = "player-audio"
                attributes["controls"] = ""
            }
        }
        script {
            src = "/static/js/player.js"
        }
    }
}

private fun HEAD.montserratFont() {
    link(rel = "preconnect", href = "https://fonts.googleapis.com")
    link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "anonymous" }
    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;600;700&display=swap")
}
