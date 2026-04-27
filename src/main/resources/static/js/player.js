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

document.getElementById('player-audio').addEventListener('timeupdate', function() {
    var cur = this.currentTime;
    if (currentEpisodeId && ws.readyState === WebSocket.OPEN && Math.abs(cur - lastReportedTime) > 0.5) {
        ws.send(JSON.stringify({ episodeId: currentEpisodeId, progressMs: Math.floor(cur * 1000) }));
        lastReportedTime = cur;
    }
});

function playEpisode(id, url, title) {
    var audio = document.getElementById('player-audio');
    currentEpisodeId = id;
    lastReportedTime = 0;
    document.getElementById('player-title').textContent = title;
    document.getElementById('player-bar').style.display = 'flex';
    audio.src = url;
    audio.play().catch(function(e) { console.error('Playback failed:', e); });
}
