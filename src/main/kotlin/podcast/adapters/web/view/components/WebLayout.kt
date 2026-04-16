package podcast.adapters.web.view.components

import kotlinx.html.*

fun HTML.layout(titleText: String, content: BODY.() -> Unit) {
    head {
        montserratFont()
        link(rel = "stylesheet", href = "/static/css/style.css")
        script { src = "https://unpkg.com/htmx.org@1.9.10" }
        title { +titleText }
    }
    body {
        id = "main-body"
        content()
    }
}

private fun HEAD.montserratFont() {
    link(rel = "preconnect", href = "https://fonts.googleapis.com")
    link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "anonymous" }
    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;700&display=swap")
}