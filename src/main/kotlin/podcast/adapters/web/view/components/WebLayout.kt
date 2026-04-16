package podcast.adapters.web.view.components

import kotlinx.html.*

fun HTML.layout(titleText: String, content: FlowContent.() -> Unit) {
    head {
        montserratFont()
        link(rel = "stylesheet", href = "/static/css/style.css")
        script { src = "https://unpkg.com/htmx.org@1.9.10" }
        title { +titleText }
    }
    body {
        id = "main-body"
        style {
            unsafe {
                +"""
                        .fab {
                            position: fixed; top: 20px; right: 20px;
                            width: 44px; height: 44px; border-radius: 50%;
                            background: #333; color: white; border: none;
                            font-size: 24px; cursor: pointer; box-shadow: 0 4px 8px rgba(0,0,0,0.2);
                            display: flex; align-items: center; justify-content: center; z-index: 100;
                        }
                        #add-feed-modal {
                            display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%;
                            background: rgba(0,0,0,0.6); z-index: 2000; align-items: center; justify-content: center;
                        }
                        #add-feed-modal.open { display: flex; }
                        .modal-content {
                            background: white; padding: 30px; border-radius: 12px; width: 90%; max-width: 500px;
                            box-shadow: 0 10px 25px rgba(0,0,0,0.2); position: relative;
                        }
                        .close-modal {
                            position: absolute; top: 10px; right: 15px; font-size: 20px; cursor: pointer; color: #888;
                        }
                    """.trimIndent()
            }
        }
        button(classes = "fab") {
            attributes["onclick"] = "document.getElementById('add-feed-modal').classList.add('open')"
            +"+"
        }
        addFeedModal()
        div {
            id = "content-container"
            content()
        }
    }
}

private fun HEAD.montserratFont() {
    link(rel = "preconnect", href = "https://fonts.googleapis.com")
    link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "anonymous" }
    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;700&display=swap")
}