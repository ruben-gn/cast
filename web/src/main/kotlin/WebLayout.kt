import kotlinx.html.*
import podcast.addFeedModal

fun HTML.layout(titleText: String, content: FlowContent.() -> Unit) {
    head {
        montserratFont()
        link(rel = "stylesheet", href = "/static/css/style.css")
        script { src = "https://unpkg.com/htmx.org@1.9.10" }
        title { +titleText }
    }
    body {
        id = "main-body"
        button(classes = "fab") {
            attributes["onclick"] = "document.getElementById('add-feed-modal').classList.add('open')"
            +"+"
        }
        addFeedModal()
        div {
            id = "content-container"
            content()
        }
        div {
            id = "player-bar"
            button {
                id = "player-btn"
                attributes["onclick"] = "togglePlay()"
                unsafe { +"&#9654;" }
            }
            div {
                id = "player-middle"
                span {
                    id = "player-title"
                    +""
                }
                div {
                    id = "player-progress-row"
                    span { id = "player-current"; +"0:00" }
                    input(type = InputType.range) {
                        id = "player-progress"
                        attributes["min"] = "0"
                        attributes["max"] = "100"
                        attributes["value"] = "0"
                        attributes["step"] = "0.1"
                    }
                    span { id = "player-duration"; +"0:00" }
                }
            }
            audio {
                id = "player-audio"
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
    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;700&display=swap")
}