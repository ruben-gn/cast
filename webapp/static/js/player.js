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
            syncPlayButtonState();
            if (typeof updateNavActive === 'function') updateNavActive();
            syncNowPlaying();
        });
}, true);

function syncPlayButtonState() {
    if (!currentEpisodeId) return;
    setPlayIcon(audio.paused);
}

document.addEventListener('htmx:afterSettle', syncPlayButtonState);

document.addEventListener('DOMContentLoaded', function() {
    fetch('/api/queue')
        .then(function(r) { return r.json(); })
        .then(function(episodes) { updateQueueBadge(episodes.length); })
        .catch(function() {});
});

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

var ICON_PLAY_LG = '<svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>';
var ICON_PAUSE_LG = '<svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>';

function setPlayIcon(paused) {
    var rowBtn = episodeBtn(currentEpisodeId);
    if (rowBtn) rowBtn.innerHTML = paused ? ICON_PLAY : ICON_PAUSE;
    var barBtn = document.getElementById('player-playpause');
    if (barBtn) barBtn.innerHTML = paused ? ICON_PLAY_LG : ICON_PAUSE_LG;
    var npBtn = document.getElementById('np-playpause');
    if (npBtn) npBtn.innerHTML = paused ? ICON_PLAY_LG : ICON_PAUSE_LG;
}

function togglePlayPause(e) {
    if (e) e.stopPropagation();
    if (!currentEpisodeId) return;
    if (audio.paused) audio.play().catch(function() {}); else audio.pause();
}

function seekBack(e) {
    if (e) e.stopPropagation();
    if (!currentEpisodeId) return;
    audio.currentTime = Math.max(0, audio.currentTime - 15);
}

function seekForward(e) {
    if (e) e.stopPropagation();
    if (!currentEpisodeId || !audio.duration) return;
    audio.currentTime = Math.min(audio.duration, audio.currentTime + 30);
}

function formatTime(sec) {
    if (!sec || isNaN(sec)) return '0:00';
    var s = Math.floor(sec);
    var h = Math.floor(s / 3600);
    var m = Math.floor((s % 3600) / 60);
    var ss = s % 60;
    var mm = h > 0 && m < 10 ? '0' + m : '' + m;
    var sss = ss < 10 ? '0' + ss : '' + ss;
    return (h > 0 ? h + ':' : '') + mm + ':' + sss;
}

var npScrubbing = false;

function syncNowPlaying() {
    var view = document.getElementById('now-playing');
    if (!view) return;
    var empty = document.getElementById('np-empty');
    var body = document.getElementById('np-bodyx');
    if (!currentEpisodeId) {
        if (empty) empty.style.display = 'block';
        if (body) body.style.display = 'none';
        return;
    }
    if (empty) empty.style.display = 'none';
    if (body) body.style.display = 'flex';
    var art = document.getElementById('np-artwork');
    if (art) {
        if (currentArtwork) { art.src = currentArtwork; art.style.display = ''; }
        else { art.removeAttribute('src'); art.style.display = 'none'; }
    }
    var t = document.getElementById('np-title');
    if (t) t.textContent = currentTitle;
    var p = document.getElementById('np-podcast');
    if (p) p.textContent = currentPodcast;
    setPlayIcon(audio.paused);
    updateNowPlayingProgress();
}

function updateNowPlayingProgress() {
    var view = document.getElementById('now-playing');
    if (!view) return;
    var dur = audio.duration || 0;
    var cur = audio.currentTime || 0;
    var elapsed = document.getElementById('np-elapsed');
    var duration = document.getElementById('np-duration');
    var scrubber = document.getElementById('np-scrubber');
    if (elapsed) elapsed.textContent = formatTime(cur);
    if (duration) duration.textContent = formatTime(dur);
    if (scrubber && !npScrubbing) scrubber.value = dur > 0 ? Math.round(cur / dur * 1000) : 0;
}

var currentEpisodeId = null;
var currentArtwork = '';
var currentPodcast = '';
var currentTitle = '';
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

function isPlayed(id) {
    if (!id) return false;
    var btn = episodeBtn(id);
    var item = btn ? btn.closest('.episode-item') : null;
    return item ? item.classList.contains('is-played') : false;
}

function markPlayed(id) {
    if (!id) return;
    var btn = episodeBtn(id);
    if (!btn) return;
    var item = btn.closest('.episode-item');
    if (!item) return;
    if (item.closest('.recent-page')) {
        item.style.transition = 'opacity 0.25s ease, transform 0.25s ease';
        item.style.opacity = '0';
        item.style.transform = 'translateX(16px)';
        setTimeout(function() { item.remove(); }, 260);
        return;
    }
    item.classList.add('is-played');
    var toggleBtn = item.querySelector('.episode-played-btn');
    if (toggleBtn) {
        toggleBtn.classList.add('is-played');
        toggleBtn.dataset.played = 'true';
        toggleBtn.title = 'Mark as unplayed';
    }
}

function unmarkPlayed(id) {
    if (!id) return;
    var btn = episodeBtn(id);
    if (!btn) return;
    var item = btn.closest('.episode-item');
    if (!item) return;
    item.classList.remove('is-played');
    var toggleBtn = item.querySelector('.episode-played-btn');
    if (toggleBtn) {
        toggleBtn.classList.remove('is-played');
        toggleBtn.dataset.played = 'false';
        toggleBtn.title = 'Mark as played';
    }
}

function togglePlayed(btn) {
    var id = btn.dataset.id;
    var played = btn.dataset.played === 'true';
    var method = played ? 'DELETE' : 'POST';
    fetch('/api/episodes/' + encodeURIComponent(id) + '/played', {method: method})
        .then(function(r) {
            if (!r.ok) return;
            if (played) unmarkPlayed(id); else markPlayed(id);
            btn.dataset.played = (!played).toString();
            btn.classList.toggle('is-played', !played);
            btn.title = !played ? 'Mark as unplayed' : 'Mark as played';
        })
        .catch(function() {});
}

function markAllEpisodesPlayed() {
    document.querySelectorAll('.episode-item').forEach(function (el) {
        el.classList.add('is-played');
    });
}

audio.addEventListener('timeupdate', function () {
    var cur = this.currentTime;
    if (currentEpisodeId && ws && ws.readyState === WebSocket.OPEN && Math.abs(cur - lastReportedTime) > 0.5) {
        ws.send(JSON.stringify({type: 'update', episodeId: currentEpisodeId, progressMs: Math.floor(cur * 1000)}));
        lastReportedTime = cur;
    }
    updateEpisodeProgress(cur, this.duration);
    var fill = document.getElementById('player-progress-fill');
    if (fill && this.duration) fill.style.width = Math.round(cur / this.duration * 100) + '%';
    updateNowPlayingProgress();
});

function updateEpisodeProgress(cur, duration) {
    if (!currentEpisodeId || !duration) return;
    var btn = episodeBtn(currentEpisodeId);
    if (!btn) return;
    var item = btn.closest('.episode-item');
    if (!item) return;
    var bar = item.querySelector('.episode-progress-bar');
    if (!bar) {
        bar = document.createElement('div');
        bar.className = 'episode-progress-bar';
        bar.innerHTML = '<div class="episode-progress-fill"></div>';
        item.appendChild(bar);
    }
    bar.querySelector('.episode-progress-fill').style.width = Math.round(cur / duration * 100) + '%';
}

audio.addEventListener('play', function () {
    setPlayIcon(false);
});

audio.addEventListener('pause', function () {
    setPlayIcon(true);
    var cur = this.currentTime;
    if (currentEpisodeId && ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({type: 'update', episodeId: currentEpisodeId, progressMs: Math.floor(cur * 1000)}));
        lastReportedTime = cur;
    }
});

audio.addEventListener('ended', function () {
    setPlayIcon(true);
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
    if (audio.readyState >= 3) {
        audio.currentTime = seekTo;
    } else {
        audio.addEventListener('canplay', function onCanPlay() {
            audio.removeEventListener('canplay', onCanPlay);
            audio.currentTime = seekTo;
        });
    }
}

function updateQueueBadge(count) {
    var badge = document.getElementById('queue-badge');
    if (badge) badge.textContent = count > 0 ? String(count) : '';
}

function playNextInQueue() {
    fetch('/api/queue')
        .then(function (r) { return r.json(); })
        .then(function (episodes) {
            if (!episodes || episodes.length === 0) return;
            var next = episodes[0];
            fetch('/queue/' + encodeURIComponent(next.id), {method: 'DELETE'});
            updateQueueBadge(episodes.length - 1);
            playEpisodeData(next.id, next.audioUrl, next.title, next.podcastImage || '', next.podcastName || '');
        })
        .catch(function () {});
}

function playEpisode(el) {
    playEpisodeData(el.dataset.id, el.dataset.audioUrl, el.dataset.title, el.dataset.artwork || '', el.dataset.podcast || '');
}

function playEpisodeData(id, url, title, artwork, podcast) {
    currentArtwork = artwork || '';
    currentPodcast = podcast || '';
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
    currentTitle = title;
    document.getElementById('player-title').textContent = title;
    var podcastEl = document.getElementById('player-podcast');
    if (podcastEl) podcastEl.textContent = podcast || '';
    var artEl = document.getElementById('player-artwork');
    if (artEl) {
        if (artwork) { artEl.src = artwork; artEl.style.display = ''; }
        else { artEl.removeAttribute('src'); artEl.style.display = 'none'; }
    }
    document.body.classList.add('has-playback');
    if (typeof syncNowPlaying === 'function') syncNowPlaying();
    audio.src = url;
    audio.play().catch(function (e) {
        console.error('Playback failed:', e);
    });
    var conn = getWs();
    var wasPlayed = isPlayed(id);
    if (wasPlayed) unmarkPlayed(id);
    var wsMsg = wasPlayed
        ? JSON.stringify({type: 'start', episodeId: id, startPositionMs: 0})
        : JSON.stringify({type: 'get', episodeId: id});
    if (conn.readyState === WebSocket.OPEN) {
        conn.send(wsMsg);
    } else {
        conn.addEventListener('open', function onOpen() {
            conn.removeEventListener('open', onOpen);
            conn.send(wsMsg);
        });
    }
}

document.addEventListener('input', function (e) {
    if (e.target && e.target.id === 'np-scrubber') {
        npScrubbing = true;
        var dur = audio.duration || 0;
        var el = document.getElementById('np-elapsed');
        if (el) el.textContent = formatTime(dur * (e.target.value / 1000));
    }
});

document.addEventListener('change', function (e) {
    if (e.target && e.target.id === 'np-scrubber') {
        var dur = audio.duration || 0;
        if (dur > 0) audio.currentTime = dur * (e.target.value / 1000);
        npScrubbing = false;
    }
});

document.addEventListener('htmx:afterSettle', syncNowPlaying);

function getDragAfterElement(container, y) {
    var rows = Array.prototype.slice.call(container.querySelectorAll('.queue-row:not(.dragging)'));
    var closest = {offset: -Infinity, element: null};
    for (var i = 0; i < rows.length; i++) {
        var box = rows[i].getBoundingClientRect();
        var offset = y - box.top - box.height / 2;
        if (offset < 0 && offset > closest.offset) closest = {offset: offset, element: rows[i]};
    }
    return closest.element;
}

document.addEventListener('dragstart', function (e) {
    var row = e.target.closest ? e.target.closest('.queue-row') : null;
    if (!row) return;
    row.classList.add('dragging');
    if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move';
});

document.addEventListener('dragover', function (e) {
    var list = document.getElementById('queue-list');
    if (!list) return;
    var dragging = list.querySelector('.queue-row.dragging');
    if (!dragging) return;
    e.preventDefault();
    var after = getDragAfterElement(list, e.clientY);
    if (after == null) list.appendChild(dragging);
    else list.insertBefore(dragging, after);
});

document.addEventListener('dragend', function (e) {
    var row = e.target.closest ? e.target.closest('.queue-row') : null;
    if (!row) return;
    row.classList.remove('dragging');
    var list = document.getElementById('queue-list');
    if (!list) return;
    var rows = Array.prototype.slice.call(list.querySelectorAll('.queue-row'));
    rows.forEach(function (r, i) {
        var pos = r.querySelector('.queue-position');
        if (pos) pos.textContent = i + 1;
    });
    var ids = rows.map(function (r) { return r.dataset.id; });
    fetch('/api/queue', {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({episodeIds: ids}),
    }).then(function (r) {
        if (!r.ok) reloadQueue();
    }).catch(function () { reloadQueue(); });
});

function reloadQueue() {
    fetch('/queue', {headers: {'HX-Request': 'true'}})
        .then(function (r) { return r.text(); })
        .then(function (html) {
            var el = document.getElementById('content-container');
            if (el) { el.outerHTML = html; htmx.process(document.getElementById('content-container')); }
        })
        .catch(function () {});
}
