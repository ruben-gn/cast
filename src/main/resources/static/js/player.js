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
