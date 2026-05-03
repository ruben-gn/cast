window.addEventListener('popstate', function(e) {
    e.stopImmediatePropagation();
    fetch(location.href, { headers: { 'HX-Request': 'true' } })
        .then(function(r) { return r.text(); })
        .then(function(html) {
            var el = document.getElementById('content-container');
            el.outerHTML = html;
            htmx.process(document.getElementById('content-container'));
            checkDescriptionOverflow();
        });
}, true);

function checkDescriptionOverflow() {
    document.querySelectorAll('.description-container').forEach(function(el) {
        if (el.scrollHeight <= el.clientHeight + 1) {
            el.querySelector('.description-fade').style.display = 'none';
            var item = el.closest('.episode-item');
            var toggle = item && item.querySelector('.episode-toggle');
            if (toggle) {
                toggle.disabled = true;
                item.querySelector('.episode-header').style.cursor = 'default';
            }
        }
    });
}

document.addEventListener('DOMContentLoaded', checkDescriptionOverflow);
document.addEventListener('htmx:afterSettle', checkDescriptionOverflow);

function handleSubResult(event) {
    if (event.detail.successful) {
        document.getElementById('add-feed-modal').close();
    } else {
        var err = document.getElementById('sub-error');
        if (err) { err.textContent = 'Could not add podcast — check the RSS URL and try again.'; err.classList.add('visible'); }
    }
}

var ICON_PLAY = '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>';
var ICON_PAUSE = '<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>';
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
    if (btn) btn.innerHTML = ICON_PAUSE;
});

audio.addEventListener('pause', function() {
    var btn = episodeBtn(currentEpisodeId);
    if (btn) btn.innerHTML = ICON_PLAY;
});

audio.addEventListener('ended', function() {
    var btn = episodeBtn(currentEpisodeId);
    if (btn) btn.innerHTML = ICON_PLAY;
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
    if (old) old.innerHTML = ICON_PLAY;

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
