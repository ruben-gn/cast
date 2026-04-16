package podcast.adapters.web.view.components

import kotlinx.html.*

fun HTML.layout(titleText: String, content: BODY.() -> Unit) {
    head {
        montserratFont()
        script { src = "https://unpkg.com/htmx.org@1.9.10" }
        script { src = "/static/js/podcast.js" }
        title { +titleText }
    }
    body {
        style = "font-family: 'Montserrat', sans-serif; background-color: #e4e4e4; margin: 0; padding: 40px; color: #333;"
        id = "main-body"
        content()
    }
}

private fun HEAD.montserratFont() {
    link(rel = "preconnect", href = "https://fonts.googleapis.com")
    link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "anonymous" }
    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;700&display=swap")
}