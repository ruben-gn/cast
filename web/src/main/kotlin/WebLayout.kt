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
            unsafe {
                +"""
                    // Intercept popstate in the capture phase so we handle it before HTMX does.
                    // We fetch the URL ourselves with HX-Request:true and swap only #content-container,
                    // which means the player bar is never touched.
                    window.addEventListener('popstate', function(e) {
                        e.stopImmediatePropagation();
                        fetch(location.href, { headers: { 'HX-Request': 'true' } })
                            .then(function(r) { return r.text(); })
                            .then(function(html) {
                                var el = document.getElementById('content-container');
                                el.outerHTML = html;
                                htmx.process(document.getElementById('content-container'));
                            });
                    }, true);

                    var playerAudio = document.getElementById('player-audio');
                    var playerBar = document.getElementById('player-bar');
                    var playerTitle = document.getElementById('player-title');
                    var playerBtn = document.getElementById('player-btn');
                    var playerProgress = document.getElementById('player-progress');
                    var playerCurrent = document.getElementById('player-current');
                    var playerDuration = document.getElementById('player-duration');

                    var currentEpisodeId = null;
                    var lastReportedTime = 0;
                    var ws = new WebSocket('ws://' + window.location.host + '/api/playback');

                    function fmt(s) {
                        if (isNaN(s) || s < 0) return '0:00';
                        var m = Math.floor(s / 60);
                        var sec = Math.floor(s % 60);
                        return m + ':' + (sec < 10 ? '0' + sec : sec);
                    }

                    playerAudio.addEventListener('play',  function() { playerBtn.innerHTML = '&#9646;&#9646;'; });
                    playerAudio.addEventListener('pause', function() { playerBtn.innerHTML = '&#9654;'; });
                    playerAudio.addEventListener('ended', function() { playerBtn.innerHTML = '&#9654;'; playerProgress.value = 0; });

                    playerAudio.addEventListener('loadedmetadata', function() {
                        playerDuration.textContent = fmt(playerAudio.duration);
                        playerProgress.value = 0;
                    });

                    playerAudio.addEventListener('timeupdate', function() {
                        var cur = playerAudio.currentTime;
                        var dur = playerAudio.duration;
                        playerCurrent.textContent = fmt(cur);
                        if (!isNaN(dur) && dur > 0) {
                            playerProgress.value = (cur / dur) * 100;
                        }

                        if (currentEpisodeId && ws.readyState === WebSocket.OPEN && Math.abs(cur - lastReportedTime) > 0.5) {
                            ws.send(JSON.stringify({
                                episodeId: currentEpisodeId,
                                progressMs: Math.floor(cur * 1000)
                            }));
                            lastReportedTime = cur;
                        }
                    });

                    playerProgress.addEventListener('input', function() {
                        var dur = playerAudio.duration;
                        if (!isNaN(dur) && dur > 0) {
                            playerAudio.currentTime = (this.value / 100) * dur;
                        }
                    });

                    function playEpisode(id, url, title) {
                        currentEpisodeId = id;
                        lastReportedTime = 0;
                        playerAudio.src = url;
                        playerTitle.textContent = title;
                        playerBar.style.display = 'flex';
                        playerProgress.value = 0;
                        playerCurrent.textContent = '0:00';
                        playerDuration.textContent = '0:00';
                        
                        var playPromise = playerAudio.play();
                        if (playPromise !== undefined) {
                            playPromise.catch(function(error) {
                                console.error("Playback failed:", error);
                            });
                        }
                    }

                    function togglePlay() {
                        if (playerAudio.paused) { 
                            var playPromise = playerAudio.play();
                            if (playPromise !== undefined) {
                                playPromise.catch(function(error) { console.error("Playback failed:", error); });
                            }
                        } else { 
                            playerAudio.pause(); 
                        }
                    }
                """.trimIndent()
            }
        }
    }
}

private fun HEAD.montserratFont() {
    link(rel = "preconnect", href = "https://fonts.googleapis.com")
    link(rel = "preconnect", href = "https://fonts.gstatic.com") { attributes["crossorigin"] = "anonymous" }
    link(rel = "stylesheet", href = "https://fonts.googleapis.com/css2?family=Montserrat:wght@400;700&display=swap")
}