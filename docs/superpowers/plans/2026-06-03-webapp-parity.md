# Webapp Non-Native Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the SSR webapp to feature parity with the Android app for non-native UI: a Now Playing view, an episode detail page, queue drag-reorder, and a redesigned player bar with artwork + transport controls.

**Architecture:** The webapp is Hono SSR + HTMX with one persistent `<audio id="player-audio">` in `Layout` and `static/js/player.js` holding all playback state. The two new "screens" are server-rendered shells filled and driven live by `player.js`. No backend changes — `GET /api/episodes/{id}` and `PUT /api/queue` already exist.

**Tech Stack:** Bun, Hono, hono/jsx (TSX), HTMX 1.9, vanilla JS, plain CSS.

**Testing note:** The webapp has **no automated test suite** (tests live in the Kotlin modules). Each task ends with a **manual verification** step run against a live backend, then a commit. To run locally: start the backend (`./gradlew run`, serves :8100) and in another shell `cd webapp && bun run server.tsx` (serves :3000). Open `http://localhost:3000`.

**Reference design:** `docs/superpowers/specs/2026-06-03-webapp-parity-design.md`

---

### Task 1: Shared `playEpisode(this)` refactor + null-safe played helpers

Foundation for everything else: play buttons pass a single element carrying all `data-*` (incl. artwork/podcast), and the played-toggle helpers stop assuming an `.episode-item` ancestor (the episode detail page has none).

**Files:**
- Modify: `webapp/static/js/player.js`
- Modify: `webapp/components/EpisodeItem.tsx`
- Modify: `webapp/components/QueuePage.tsx`

- [ ] **Step 1: Split `playEpisode` into element-reader + data fn**

In `webapp/static/js/player.js`, replace the function header at the start of `playEpisode` (currently `function playEpisode(id, url, title) {`) by introducing a wrapper. Change:

```js
function playEpisode(id, url, title) {
    if (currentEpisodeId === id) {
```

to:

```js
function playEpisode(el) {
    playEpisodeData(el.dataset.id, el.dataset.audioUrl, el.dataset.title, el.dataset.artwork || '', el.dataset.podcast || '');
}

function playEpisodeData(id, url, title, artwork, podcast) {
    currentArtwork = artwork || '';
    currentPodcast = podcast || '';
    if (currentEpisodeId === id) {
```

- [ ] **Step 2: Add module state vars for artwork/podcast/title**

Near the existing `var currentEpisodeId = null;` (around line 44), add:

```js
var currentArtwork = '';
var currentPodcast = '';
var currentTitle = '';
```

- [ ] **Step 3: Update the internal `playNextInQueue` caller**

In `playNextInQueue`, change `playEpisode(next.id, next.audioUrl, next.title);` to:

```js
playEpisodeData(next.id, next.audioUrl, next.title, next.podcastImage || '', next.podcastName || '');
```

- [ ] **Step 4: Make `markPlayed`/`unmarkPlayed` null-safe**

In `markPlayed`, after `var item = btn.closest('.episode-item');` add a guard so the detail page (no `.episode-item`) doesn't crash. The block becomes:

```js
function markPlayed(id) {
    if (!id) return;
    var btn = episodeBtn(id);
    if (!btn) return;
    var item = btn.closest('.episode-item');
    if (!item) return;
    if (item.closest('.recent-page')) {
```

Apply the same `if (!item) return;` guard in `unmarkPlayed` after its `var item = ...` line.

- [ ] **Step 5: Make `togglePlayed` update the clicked button directly**

Replace the body of `togglePlayed` with:

```js
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
```

- [ ] **Step 6: Update `EpisodeItem.tsx` play button to pass the element**

In `webapp/components/EpisodeItem.tsx`, change the play `<button>` (currently has `data-id`, `data-audio-url`, `data-title`, and `onclick="playEpisode(this.dataset.id, this.dataset.audioUrl, this.dataset.title)"`) to:

```jsx
<button
    class="episode-play-btn"
    data-id={episode.id}
    data-audio-url={episode.audioUrl}
    data-title={episode.title}
    data-artwork={episode.podcastImage ?? ''}
    data-podcast={episode.podcastName ?? ''}
    onclick="playEpisode(this)"
    title={`Play ${episode.title}`}
>
```

- [ ] **Step 7: Update `QueuePage.tsx` play button to pass the element**

In `webapp/components/QueuePage.tsx`, change the `QueueRow` play `<button>` to:

```jsx
<button
    class="episode-play-btn episode-play-btn--small"
    data-id={episode.id}
    data-audio-url={episode.audioUrl}
    data-title={episode.title}
    data-artwork={episode.podcastImage ?? ''}
    data-podcast={episode.podcastName ?? ''}
    onclick="playEpisode(this)"
    title={`Play ${episode.title}`}
>
```

- [ ] **Step 8: Manual verification**

With backend + webapp running, open `http://localhost:3000`. Play an episode from Recent, from a podcast detail page, and from the queue. Expected: audio plays in all three; the play/pause icon on the row toggles; no JS console errors. (The bar still looks unchanged — that's Task 2.)

- [ ] **Step 9: Commit**

```bash
git add webapp/static/js/player.js webapp/components/EpisodeItem.tsx webapp/components/QueuePage.tsx
git commit -m "refactor(webapp): playEpisode(this) + artwork data-attrs, null-safe played helpers"
```

---

### Task 2: Player bar redesign (artwork + transport + progress line)

Rebuild `#player-bar` to match Android: artwork, title/podcast, ⏪ ▶/⏸ ⏩ (15s/30s), thin progress line, clicking the body opens Now Playing.

**Files:**
- Modify: `webapp/components/Layout.tsx`
- Modify: `webapp/static/js/player.js`
- Modify: `webapp/static/css/style.css`

- [ ] **Step 1: Replace the player-bar markup in `Layout.tsx`**

Replace the existing `<div id="player-bar">…</div>` block (the one containing `.player-info`, `player-now-playing`, `#player-title`, and `<audio id="player-audio" controls>`) with:

```jsx
<div id="player-bar">
  <div class="player-bar-main" hx-get="/now-playing" hx-target="#content-container" hx-swap="outerHTML" hx-push-url="true">
    <img id="player-artwork" class="player-artwork" alt="" />
    <div class="player-info">
      <span id="player-title" class="player-title"></span>
      <span id="player-podcast" class="player-podcast"></span>
    </div>
  </div>
  <div class="player-controls">
    <button class="player-ctrl-btn" onclick="seekBack(event)" aria-label="Back 15 seconds">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z"/></svg>
    </button>
    <button id="player-playpause" class="player-ctrl-btn player-ctrl-btn--main" onclick="togglePlayPause(event)" aria-label="Play or pause">
      <svg viewBox="0 0 24 24" width="26" height="26" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
    </button>
    <button class="player-ctrl-btn" onclick="seekForward(event)" aria-label="Forward 30 seconds">
      <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor"><path d="M13 6v12l8.5-6L13 6zm-.5 6L4 6v12l8.5-6z"/></svg>
    </button>
  </div>
  <div class="player-progress"><div id="player-progress-fill" class="player-progress-fill"></div></div>
  <audio id="player-audio"></audio>
</div>
```

- [ ] **Step 2: Add large play/pause icon constants + `setPlayIcon` in `player.js`**

After the existing `var ICON_PAUSE = '…';` line, add:

```js
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
```

- [ ] **Step 3: Add transport handlers in `player.js`**

Add these functions (anywhere at top level, e.g. just below `setPlayIcon`):

```js
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
```

- [ ] **Step 4: Drive the bar from `playEpisodeData`**

Inside `playEpisodeData`, replace these existing lines:

```js
    document.getElementById('player-title').textContent = title;
    document.getElementById('player-bar').style.display = 'flex';
    audio.src = url;
```

with:

```js
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
    syncNowPlaying();
    audio.src = url;
```

(`syncNowPlaying` is defined in Task 3; it is null-safe and harmless until then.)

- [ ] **Step 5: Route the audio listeners through `setPlayIcon`**

In the `audio.addEventListener('play', …)` handler, replace its body with `setPlayIcon(false);`.
In the `audio.addEventListener('pause', …)` handler, replace the line `var btn = episodeBtn(currentEpisodeId); if (btn) btn.innerHTML = ICON_PLAY;` with `setPlayIcon(true);` (keep the rest of the pause handler — the WS `update` send — intact).
In the `audio.addEventListener('ended', …)` handler, replace the line `var btn = episodeBtn(currentEpisodeId); if (btn) btn.innerHTML = ICON_PLAY;` with `setPlayIcon(true);`.
In `syncPlayButtonState`, replace its body with:

```js
function syncPlayButtonState() {
    if (!currentEpisodeId) return;
    setPlayIcon(audio.paused);
}
```

- [ ] **Step 6: Update progress line on timeupdate**

In the `audio.addEventListener('timeupdate', …)` handler, immediately after `updateEpisodeProgress(cur, this.duration);` add:

```js
    var fill = document.getElementById('player-progress-fill');
    if (fill && this.duration) fill.style.width = Math.round(cur / this.duration * 100) + '%';
```

- [ ] **Step 7: Replace player-bar CSS**

In `webapp/static/css/style.css`, replace the three rules `.player-info { … }`, `.player-now-playing { … }`, `#player-title { … }`, and `#player-audio { … }` (the block from line ~895 to ~925) with:

```css
.player-bar-main {
    display: flex;
    align-items: center;
    gap: 12px;
    flex: 1;
    min-width: 0;
    cursor: pointer;
}

.player-artwork {
    width: 44px;
    height: 44px;
    border-radius: var(--radius-sm);
    object-fit: cover;
    flex-shrink: 0;
}

.player-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
    min-width: 0;
    overflow: hidden;
}

.player-title {
    font-size: 13px;
    font-weight: 600;
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    color: white;
}

.player-podcast {
    font-size: 11px;
    color: rgba(255,255,255,0.6);
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
}

.player-controls {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
}

.player-ctrl-btn {
    background: none;
    border: none;
    color: white;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 4px;
    opacity: 0.85;
    transition: opacity 0.15s, transform 0.1s;
}

.player-ctrl-btn:hover { opacity: 1; }
.player-ctrl-btn--main:active { transform: scale(0.92); }

.player-progress {
    position: absolute;
    left: 0;
    right: 0;
    bottom: 0;
    height: 3px;
    background: rgba(255,255,255,0.12);
}

.player-progress-fill {
    height: 100%;
    width: 0;
    background: var(--accent);
}

#player-audio { display: none; }

body.has-playback #player-bar { display: flex; }
body.now-playing-active #player-bar { display: none; }
```

- [ ] **Step 8: Update mobile player-bar CSS**

In the `@media (max-width: 600px)` block, replace the `#player-bar { … }`, `.player-now-playing { … }`, `#player-title { … }`, and `#player-audio { … }` rules (lines ~1080–1099) with:

```css
    #player-bar {
        padding: 8px 14px;
        gap: 10px;
    }

    .player-controls {
        gap: 2px;
    }
```

- [ ] **Step 9: Manual verification**

Reload `http://localhost:3000` and play an episode. Expected: bar shows artwork + title + podcast; ⏪/▶⏸/⏩ work (15s back, 30s forward, toggle); progress line fills as it plays. Clicking the bar body navigates to `/now-playing` (will 404 until Task 3 — that's expected; just confirm the URL changes / request fires).

- [ ] **Step 10: Commit**

```bash
git add webapp/components/Layout.tsx webapp/static/js/player.js webapp/static/css/style.css
git commit -m "feat(webapp): redesign player bar with artwork + transport controls"
```

---

### Task 3: Now Playing view (`GET /now-playing`)

Full-screen client-driven view with large artwork, scrubber, and transport.

**Files:**
- Create: `webapp/components/NowPlaying.tsx`
- Modify: `webapp/server.tsx`
- Modify: `webapp/static/js/player.js`
- Modify: `webapp/components/Layout.tsx`
- Modify: `webapp/static/css/style.css`

- [ ] **Step 1: Create `NowPlaying.tsx`**

```tsx
import type {FC} from 'hono/jsx'

export const NowPlaying: FC = () => (
    <div class="now-playing-view" id="now-playing">
        <a class="back-link" onclick="history.back()" style="cursor:pointer">← Back</a>
        <div class="np-empty" id="np-empty">Nothing playing</div>
        <div class="np-body" id="np-bodyx">
            <img id="np-artwork" class="np-artwork" alt="" />
            <div class="np-meta">
                <span id="np-title" class="np-title"></span>
                <span id="np-podcast" class="np-podcast"></span>
            </div>
            <input type="range" id="np-scrubber" class="np-scrubber" min="0" max="1000" value="0" />
            <div class="np-times">
                <span id="np-elapsed">0:00</span>
                <span id="np-duration">0:00</span>
            </div>
            <div class="np-controls">
                <button class="player-ctrl-btn" onclick="seekBack(event)" aria-label="Back 15 seconds">
                    <svg viewBox="0 0 24 24" width="30" height="30" fill="currentColor"><path d="M11 18V6l-8.5 6 8.5 6zm.5-6l8.5 6V6l-8.5 6z"/></svg>
                </button>
                <button id="np-playpause" class="np-playpause" onclick="togglePlayPause(event)" aria-label="Play or pause">
                    <svg viewBox="0 0 24 24" width="34" height="34" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                </button>
                <button class="player-ctrl-btn" onclick="seekForward(event)" aria-label="Forward 30 seconds">
                    <svg viewBox="0 0 24 24" width="30" height="30" fill="currentColor"><path d="M13 6v12l8.5-6L13 6zm-.5 6L4 6v12l8.5-6z"/></svg>
                </button>
            </div>
        </div>
    </div>
)
```

- [ ] **Step 2: Add the `/now-playing` route in `server.tsx`**

Add the import near the other component imports:

```tsx
import {NowPlaying} from './components/NowPlaying'
```

Add this route (e.g. just after the `app.get('/queue', …)` route):

```tsx
app.get('/now-playing', async (c) => {
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <NowPlaying/>
    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.html(<Layout title="Now Playing — Cast">{content}</Layout>)
})
```

- [ ] **Step 3: Add `formatTime` + `syncNowPlaying` to `player.js`**

```js
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
```

- [ ] **Step 4: Wire scrubber + lifecycle hooks in `player.js`**

In the `timeupdate` handler, add at the end: `updateNowPlayingProgress();`

Add a delegated scrubber handler and a settle hook at the bottom of the file:

```js
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
```

- [ ] **Step 5: Toggle the `now-playing-active` body class**

In `Layout.tsx`, in the inline `updateNavActive` script, add this line inside the function body (after the `forEach`):

```js
          document.body.classList.toggle('now-playing-active', path === '/now-playing');
```

In `player.js`, in the `popstate` handler's `.then(function (html) { … })`, after `syncPlayButtonState();` add:

```js
            if (typeof updateNavActive === 'function') updateNavActive();
            syncNowPlaying();
```

- [ ] **Step 6: Add Now Playing CSS**

Append to `style.css`:

```css
/* Now Playing view */
.now-playing-view { max-width: 480px; margin: 0 auto; }

.np-empty { display: none; text-align: center; color: var(--text-muted); margin-top: 80px; font-size: 15px; }

.np-body { display: flex; flex-direction: column; align-items: center; gap: 20px; padding-top: 8px; }

.np-artwork {
    width: 280px; max-width: 80vw; aspect-ratio: 1; object-fit: cover;
    border-radius: var(--radius-lg); box-shadow: var(--shadow-md);
}

.np-meta { display: flex; flex-direction: column; align-items: center; gap: 6px; text-align: center; width: 100%; }
.np-title { font-size: 18px; font-weight: 700; color: var(--text); }
.np-podcast { font-size: 13px; color: var(--text-muted); }

.np-scrubber { width: 100%; accent-color: var(--accent); cursor: pointer; }

.np-times { display: flex; justify-content: space-between; width: 100%; font-size: 11px; color: var(--text-muted); }

.np-controls { display: flex; align-items: center; gap: 28px; margin-top: 8px; }

.np-controls .player-ctrl-btn { color: var(--text-muted); }
.np-controls .player-ctrl-btn:hover { color: var(--text); }

.np-playpause {
    width: 72px; height: 72px; border-radius: 50%; border: none; cursor: pointer;
    background: var(--accent); color: white;
    display: flex; align-items: center; justify-content: center;
    box-shadow: 0 4px 14px rgba(27,153,139,0.4); transition: transform 0.1s;
}
.np-playpause:active { transform: scale(0.94); }
```

- [ ] **Step 7: Manual verification**

Play an episode, click the bar → Now Playing shows artwork, title, podcast, live scrubber + times, working ⏪/▶⏸/⏩. Drag the scrubber → audio seeks on release. The mini-bar is hidden on this view. Back returns to the previous page and the bar reappears. Navigate to `/now-playing` with nothing playing → "Nothing playing".

- [ ] **Step 8: Commit**

```bash
git add webapp/components/NowPlaying.tsx webapp/server.tsx webapp/static/js/player.js webapp/components/Layout.tsx webapp/static/css/style.css
git commit -m "feat(webapp): full-screen Now Playing view with scrubber"
```

---

### Task 4: Episode detail page (`GET /episodes/:id`)

A navigable page with artwork, full notes, and play/queue/played actions; episode rows link to it.

**Files:**
- Create: `webapp/components/EpisodeDetail.tsx`
- Modify: `webapp/server.tsx`
- Modify: `webapp/components/EpisodeItem.tsx`
- Modify: `webapp/static/css/style.css`

- [ ] **Step 1: Create `EpisodeDetail.tsx`**

```tsx
import type {FC} from 'hono/jsx'
import type {Episode} from '../types'

function relativeTime(iso: string | null): string | null {
    if (!iso) return null
    const days = Math.floor((Date.now() - new Date(iso).getTime()) / 86_400_000)
    if (days <= 0) return 'Today'
    if (days === 1) return 'Yesterday'
    if (days < 7) return `${days} days ago`
    const weeks = Math.floor(days / 7)
    if (weeks < 5) return `${weeks} ${weeks === 1 ? 'week' : 'weeks'} ago`
    const months = Math.floor(days / 30)
    if (months < 12) return `${months} ${months === 1 ? 'month' : 'months'} ago`
    const years = Math.floor(days / 365)
    return `${years} ${years === 1 ? 'year' : 'years'} ago`
}

export const EpisodeDetail: FC<{episode: Episode}> = ({episode}) => {
    const showProgress = episode.progressMs > 0 && !!episode.durationMs && episode.durationMs > 0
    const meta = [episode.podcastName, relativeTime(episode.publishedAt), episode.duration].filter(Boolean).join(' · ')
    return (
        <div class="episode-detail-page">
            <a class="back-link" onclick="history.back()" style="cursor:pointer">← Back</a>
            <div class="episode-detail-header">
                {episode.podcastImage && (
                    <img class="episode-detail-cover" src={episode.podcastImage} alt="" loading="lazy"/>
                )}
                <div class="episode-detail-info">
                    <h1 class="episode-detail-title">{episode.title}</h1>
                    {meta && <p class="episode-detail-meta">{meta}</p>}
                </div>
            </div>
            <div class="episode-detail-actions">
                <button
                    class="episode-play-btn"
                    data-id={episode.id}
                    data-audio-url={episode.audioUrl}
                    data-title={episode.title}
                    data-artwork={episode.podcastImage ?? ''}
                    data-podcast={episode.podcastName ?? ''}
                    onclick="playEpisode(this)"
                    title={`Play ${episode.title}`}
                >
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><path d="M8 5v14l11-7z"/></svg>
                </button>
                <button
                    class="episode-queue-btn"
                    hx-post={`/queue/${encodeURIComponent(episode.id)}`}
                    hx-swap="none"
                    title="Add to queue"
                >
                    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round">
                        <line x1="8" y1="6" x2="21" y2="6"/><line x1="8" y1="12" x2="21" y2="12"/><line x1="8" y1="18" x2="21" y2="18"/>
                        <line x1="3" y1="6" x2="3.01" y2="6"/><line x1="3" y1="12" x2="3.01" y2="12"/><line x1="3" y1="18" x2="3.01" y2="18"/>
                    </svg>
                </button>
                <button
                    class={`episode-played-btn${episode.played ? ' is-played' : ''}`}
                    data-id={episode.id}
                    data-played={episode.played ? 'true' : 'false'}
                    onclick="togglePlayed(this)"
                    title={episode.played ? 'Mark as unplayed' : 'Mark as played'}
                >
                    <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
                        <polyline points="20 6 9 17 4 12"/>
                    </svg>
                </button>
            </div>
            {showProgress && (
                <div class="episode-progress-bar episode-detail-progress">
                    <div class="episode-progress-fill" style={`width:${Math.round(episode.progressMs / episode.durationMs! * 100)}%`}></div>
                </div>
            )}
            {episode.description.trim() !== '' && (
                <div class="episode-detail-description" dangerouslySetInnerHTML={{__html: episode.description.replace(/<a\s/gi, '<a target="_blank" rel="noopener noreferrer" ')}}/>
            )}
        </div>
    )
}
```

- [ ] **Step 2: Add the `/episodes/:id` route in `server.tsx`**

Add the import:

```tsx
import {EpisodeDetail} from './components/EpisodeDetail'
```

Add the route (place it near the other GET routes, e.g. after the `/now-playing` route):

```tsx
app.get('/episodes/:id', async (c) => {
    const id = c.req.param('id')
    const res = await fetch(`${KOTLIN_API}/api/episodes/${encodeURIComponent(id)}`)
    if (!res.ok) return c.notFound()
    const episode: Episode = await res.json()
    const isHtmx = c.req.header('HX-Request') === 'true'
    const content = <EpisodeDetail episode={episode}/>
    if (isHtmx) {
        return c.html(
            <div id="content-container">
                <div class="page-content">{content}</div>
            </div>
        )
    }
    return c.html(<Layout title={episode.title}>{content}</Layout>)
})
```

- [ ] **Step 3: Make the episode-row title link to the detail page**

In `EpisodeItem.tsx`, change the static header wrapper:

```jsx
                <div class="episode-header episode-header--static">
                    <EpisodeRow episode={episode}/>
                </div>
```

to:

```jsx
                <div
                    class="episode-header"
                    hx-get={`/episodes/${encodeURIComponent(episode.id)}`}
                    hx-target="#content-container"
                    hx-swap="outerHTML"
                    hx-push-url="true"
                >
                    <EpisodeRow episode={episode}/>
                </div>
```

- [ ] **Step 4: Add episode-detail CSS**

Append to `style.css`:

```css
/* Episode detail page */
.episode-detail-page { max-width: 720px; }

.episode-detail-header { display: flex; gap: 18px; align-items: flex-start; margin-bottom: 18px; }

.episode-detail-cover {
    width: 96px; height: 96px; object-fit: cover; border-radius: var(--radius-md);
    flex-shrink: 0; box-shadow: var(--shadow-sm);
}

.episode-detail-title { margin: 0 0 6px; font-size: 20px; font-weight: 700; color: var(--text); line-height: 1.3; }
.episode-detail-meta { margin: 0; font-size: 13px; color: var(--text-muted); font-weight: 500; }

.episode-detail-actions { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; }

.episode-detail-progress {
    position: static; height: 4px; border-radius: 2px; overflow: hidden; margin-bottom: 20px;
}

.episode-detail-description {
    font-size: 14px; line-height: 1.7; color: var(--text); word-wrap: break-word;
}
.episode-detail-description a { color: var(--accent); }
```

- [ ] **Step 5: Manual verification**

Click an episode title in Recent / a podcast page → navigates to the episode page with artwork, full notes (links open in a new tab), and working Play / +Queue / ✓Played (the played check toggles on the page). Back returns. Confirm the row action buttons still work without triggering navigation.

- [ ] **Step 6: Commit**

```bash
git add webapp/components/EpisodeDetail.tsx webapp/server.tsx webapp/components/EpisodeItem.tsx webapp/static/css/style.css
git commit -m "feat(webapp): episode detail page reachable from episode rows"
```

---

### Task 5: Queue drag-and-drop reorder

Drag handle per row; on drop, persist order via `PUT /api/queue`.

**Files:**
- Modify: `webapp/components/QueuePage.tsx`
- Modify: `webapp/static/js/player.js`
- Modify: `webapp/static/css/style.css`

- [ ] **Step 1: Add handle + draggable + `data-id` to `QueueRow`**

In `QueuePage.tsx`, change the `QueueRow` opening `<div>` from:

```jsx
    <div class="queue-row" id={`queue-row-${episode.id}`}>
        <span class="queue-position">{position}</span>
```

to:

```jsx
    <div class="queue-row" id={`queue-row-${episode.id}`} data-id={episode.id} draggable="true">
        <span class="queue-drag-handle" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor"><circle cx="9" cy="6" r="1.6"/><circle cx="15" cy="6" r="1.6"/><circle cx="9" cy="12" r="1.6"/><circle cx="15" cy="12" r="1.6"/><circle cx="9" cy="18" r="1.6"/><circle cx="15" cy="18" r="1.6"/></svg>
        </span>
        <span class="queue-position">{position}</span>
```

- [ ] **Step 2: Add drag-and-drop logic to `player.js`**

Append at the bottom of `player.js`:

```js
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
```

- [ ] **Step 3: Proxy `PUT /api/queue` through the webapp**

The browser calls `/api/queue` (PUT) on the Bun server, which must forward to Kotlin. In `server.tsx`, add next to the existing `app.get('/api/queue', …)`:

```tsx
app.put('/api/queue', async (c) => {
    const body = await c.req.text()
    const res = await fetch(`${KOTLIN_API}/api/queue`, {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body,
    })
    return new Response(res.body, {
        status: res.status,
        headers: {'Content-Type': res.headers.get('content-type') ?? 'application/json'},
    })
})
```

- [ ] **Step 4: Add drag-handle CSS**

Append to `style.css`:

```css
.queue-drag-handle {
    display: flex; align-items: center; color: var(--text-muted);
    cursor: grab; flex-shrink: 0; touch-action: none;
}
.queue-drag-handle:active { cursor: grabbing; }
.queue-row.dragging { opacity: 0.5; box-shadow: var(--shadow-md); }
```

- [ ] **Step 5: Manual verification**

On the queue page, drag a row by anywhere on the row to a new slot. Expected: rows reorder live, position numbers renumber on drop, and the new order **persists after a full page reload**. Play and remove buttons still work. Removing or adding episodes still updates the badge.

- [ ] **Step 6: Commit**

```bash
git add webapp/components/QueuePage.tsx webapp/static/js/player.js webapp/static/css/style.css
git commit -m "feat(webapp): drag-and-drop queue reordering"
```

---

## Final verification (all features)

- [ ] Run the full smoke pass from the spec's Testing section: play→bar artwork; Now Playing scrub; episode page actions; queue drag persists; Recent/Catalog/Settings/Add-podcast/OPML/queue-badge regressions all clear. No console errors across navigations (HTMX swaps + browser back/forward).

## Self-review notes (author)

- **Spec coverage:** Now Playing (Task 3) ✓, episode detail (Task 4) ✓, queue reorder (Task 5) ✓, player-bar artwork/seek (Task 2) ✓, shared `playEpisode(this)` + artwork plumbing (Task 1) ✓, 15s/30s seek (Task 2 Step 3) ✓, manual-verify testing ✓, no backend changes (only a Bun→Kotlin PUT proxy, Task 5 Step 3) ✓.
- **Type consistency:** `playEpisodeData(id, url, title, artwork, podcast)` defined in Task 1, called in Task 1/2/3; `setPlayIcon`, `syncNowPlaying`, `updateNowPlayingProgress`, `getDragAfterElement`, `reloadQueue` each defined once and referenced consistently; `now-playing-active` / `has-playback` body classes match between JS and CSS.
- **Forward references:** `syncNowPlaying` is called in Task 2 Step 4 but defined in Task 3 Step 3 — it is null-safe (returns early when `#now-playing` is absent), so Task 2 works standalone; full behavior lands in Task 3.
