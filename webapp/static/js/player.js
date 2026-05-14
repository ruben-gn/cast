window.addEventListener('popstate', function (e) {
    e.stopImmediatePropagation();
    fetch(location.href, {headers: {'HX-Request': 'true'}})
        .then(function (r) {
            return r.text();
        })
        .then(function (html) {
            var el = document.getElementById('content-container');
            el.outerHTML = html;
            htmx.process(document.getElementById('content-container'));
        });
}, true);

function handleSubResult(event) {
    if (event.detail.successful) {
        document.getElementById('add-feed-modal').close();
    } else {
        var err = document.getElementById('sub-error');
        if (err) {
            err.textContent = 'Could not add podcast — check the RSS URL and try again.';
            err.classList.add('visible');
        }
    }
}

var ICON_PLAY = '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>';
var ICON_PAUSE = '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>';
var currentEpisodeId = null;
var lastReportedTime = 0;
var audio = document.getElementById('player-audio');
var ws = null;

function getWs() {
    if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return ws;
    ws = new WebSocket('ws://' + window.location.host + '/api/playback');
    ws.onmessage = function (event) {
        var msg = JSON.parse(event.data);
        if (msg.type === 'state') {
            if (msg.episodeId === currentEpisodeId) applyResume(msg.progressMs);
            if (msg.played) markPlayed(msg.episodeId);
        }
    };
    ws.onclose = function () { ws = null; };
    ws.onerror = function () { ws = null; };
    return ws;
}

function episodeBtn(id) {
    return id ? document.querySelector('.episode-play-btn[data-id="' + id + '"]') : null;
}

function markPlayed(id) {
    if (!id) return;
    var btn = episodeBtn(id);
    if (!btn) return;
    var extras = btn.closest('.episode-item').querySelector('.episode-extras');
    if (!extras || extras.querySelector('.episode-played-badge')) return;
    var badge = document.createElement('span');
    badge.className = 'episode-played-badge';
    badge.textContent = 'Played';
    extras.appendChild(badge);
}

audio.addEventListener('timeupdate', function () {
    var cur = this.currentTime;
    if (currentEpisodeId && ws && ws.readyState === WebSocket.OPEN && Math.abs(cur - lastReportedTime) > 0.5) {
        ws.send(JSON.stringify({type: 'update', episodeId: currentEpisodeId, progressMs: Math.floor(cur * 1000)}));
        lastReportedTime = cur;
    }
});

audio.addEventListener('play', function () {
    var btn = episodeBtn(currentEpisodeId);
    if (btn) btn.innerHTML = ICON_PAUSE;
});

audio.addEventListener('pause', function () {
    var btn = episodeBtn(currentEpisodeId);
    if (btn) btn.innerHTML = ICON_PLAY;
});

audio.addEventListener('ended', function () {
    var btn = episodeBtn(currentEpisodeId);
    if (btn) btn.innerHTML = ICON_PLAY;
    if (currentEpisodeId && ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'ended', episodeId: currentEpisodeId }));
    }
    markPlayed(currentEpisodeId);
    currentEpisodeId = null;
    if (ws) { ws.close(); ws = null; }
    playNextInQueue();
});

function applyResume(progressMs) {
    var seekTo = progressMs / 1000;
    if (seekTo <= 0) return;
    audio.addEventListener('canplay', function onCanPlay() {
        audio.removeEventListener('canplay', onCanPlay);
        audio.currentTime = seekTo;
    });
}

function playNextInQueue() {
    fetch('/api/queue')
        .then(function (r) { return r.json(); })
        .then(function (episodes) {
            if (!episodes || episodes.length === 0) return;
            var next = episodes[0];
            fetch('/queue/' + next.id, {method: 'DELETE'});
            playEpisode(next.id, next.audioUrl, next.title);
        })
        .catch(function () {});
}

function playEpisode(id, url, title) {
    if (currentEpisodeId === id) {
        if (audio.paused) {
            audio.play().catch(function (e) {
                console.error('Playback failed:', e);
            });
        } else {
            audio.pause();
        }
        return;
    }

    var old = episodeBtn(currentEpisodeId);
    if (old) old.innerHTML = ICON_PLAY;

    currentEpisodeId = id;
    lastReportedTime = 0;
    document.getElementById('player-title').textContent = title;
    document.getElementById('player-bar').style.display = 'flex';
    audio.src = url;
    audio.play().catch(function (e) {
        console.error('Playback failed:', e);
    });
    var conn = getWs();
    if (conn.readyState === WebSocket.OPEN) {
        conn.send(JSON.stringify({type: 'get', episodeId: id}));
    } else {
        conn.addEventListener('open', function onOpen() {
            conn.removeEventListener('open', onOpen);
            conn.send(JSON.stringify({type: 'get', episodeId: id}));
        });
    }
}
