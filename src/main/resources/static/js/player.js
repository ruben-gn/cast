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
var ws = new WebSocket('ws://' + window.location.host + '/api/playback');

ws.onmessage = function(event) {
    var msg = JSON.parse(event.data);
    if (msg.type === 'state' && msg.episodeId === currentEpisodeId) {
        applyResume(msg.progressMs);
    }
};

document.getElementById('player-audio').addEventListener('timeupdate', function() {
    var cur = this.currentTime;
    if (currentEpisodeId && ws.readyState === WebSocket.OPEN && Math.abs(cur - lastReportedTime) > 0.5) {
        ws.send(JSON.stringify({ type: 'update', episodeId: currentEpisodeId, progressMs: Math.floor(cur * 1000) }));
        lastReportedTime = cur;
    }
});

function applyResume(progressMs) {
    var audio = document.getElementById('player-audio');
    var seekTo = progressMs / 1000;

    function doPlay() {
        if (seekTo > 0) audio.currentTime = seekTo;
        audio.play().catch(function(e) { console.error('Playback failed:', e); });
    }

    if (seekTo > 0 && audio.readyState < 1) {
        audio.addEventListener('loadedmetadata', function onMeta() {
            audio.removeEventListener('loadedmetadata', onMeta);
            doPlay();
        });
    } else {
        doPlay();
    }
}

function playEpisode(id, url, title) {
    var audio = document.getElementById('player-audio');
    currentEpisodeId = id;
    lastReportedTime = 0;
    document.getElementById('player-title').textContent = title;
    document.getElementById('player-bar').style.display = 'flex';
    audio.src = url;
    if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'get', episodeId: id }));
    } else {
        audio.play().catch(function(e) { console.error('Playback failed:', e); });
    }
}
