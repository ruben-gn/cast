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

var currentEpisodeId = null;
var lastReportedTime = 0;
var audio = document.getElementById('player-audio');
var ws = new WebSocket('ws://' + window.location.host + '/api/playback');

function episodeBtn(id) {
    return id ? document.querySelector('.episode-play-btn[data-id="' + id + '"]') : null;
}

ws.onmessage = function(event) {
    var msg = JSON.parse(event.data);
    if (msg.type === 'state' && msg.episodeId === currentEpisodeId) {
        applyResume(msg.progressMs);
    }
};

audio.addEventListener('timeupdate', function() {
    var cur = this.currentTime;
    if (currentEpisodeId && ws.readyState === WebSocket.OPEN && Math.abs(cur - lastReportedTime) > 0.5) {
        ws.send(JSON.stringify({ type: 'update', episodeId: currentEpisodeId, progressMs: Math.floor(cur * 1000) }));
        lastReportedTime = cur;
    }
});

audio.addEventListener('play', function() {
    var btn = episodeBtn(currentEpisodeId);
    if (btn) btn.innerHTML = '&#9208;';
});

audio.addEventListener('pause', function() {
    var btn = episodeBtn(currentEpisodeId);
    if (btn) btn.innerHTML = '&#9654;';
});

audio.addEventListener('ended', function() {
    var btn = episodeBtn(currentEpisodeId);
    if (btn) btn.innerHTML = '&#9654;';
    currentEpisodeId = null;
});

function applyResume(progressMs) {
    var seekTo = progressMs / 1000;
    if (seekTo <= 0) return;
    if (audio.readyState >= 1) {
        audio.currentTime = seekTo;
    } else {
        audio.addEventListener('loadedmetadata', function onMeta() {
            audio.removeEventListener('loadedmetadata', onMeta);
            audio.currentTime = seekTo;
        });
    }
}

function playEpisode(id, url, title) {
    if (currentEpisodeId === id) {
        if (audio.paused) {
            audio.play().catch(function(e) { console.error('Playback failed:', e); });
        } else {
            audio.pause();
        }
        return;
    }

    var old = episodeBtn(currentEpisodeId);
    if (old) old.innerHTML = '&#9654;';

    currentEpisodeId = id;
    lastReportedTime = 0;
    document.getElementById('player-title').textContent = title;
    document.getElementById('player-bar').style.display = 'flex';
    audio.src = url;
    audio.play().catch(function(e) { console.error('Playback failed:', e); });
    if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'get', episodeId: id }));
    }
}
