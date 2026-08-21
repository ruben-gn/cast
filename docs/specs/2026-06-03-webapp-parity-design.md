# Webapp ↔ Android parity (non-native features)

**Date:** 2026-06-03
**Status:** implemented (round 1; later gaps tracked in [`../webapp-parity-gaps.md`](../webapp-parity-gaps.md))
**Scope:** Bring the SSR webapp (`webapp/`) up to feature parity with the Android app for
non-native UI features. Explicitly **out of scope:** Android Auto, home-screen widget, offline
progress caching, background feed refresh, lock-screen/media-session controls, playback
resumption, connectivity awareness, server-URL config.

No backend changes required. The needed endpoints already exist:
- `GET /api/episodes/{id}` — single episode (used by episode detail page)
- `PUT /api/queue` with body `{ "episodeIds": [...] }` — reorder (used by drag-and-drop)

## Gaps being closed

1. Now Playing full-screen view (artwork, scrubber, seek) — webapp only had a thin title bar.
2. Episode detail page — webapp only had an inline "Show more" expander.
3. Queue reorder — webapp was add/remove only.
4. Player-bar artwork + transport buttons — webapp bar showed title + a native `<audio controls>`.

## Architecture fit

The webapp is **Hono SSR + HTMX** with a single persistent `<audio id="player-audio">` element in
`Layout` and `static/js/player.js` holding all playback state (`currentEpisodeId`, audio element,
WebSocket). HTMX swaps page content into `#content-container`; the audio element lives **outside**
that container, so playback survives navigation.

Because playback state (position, duration, playing, current episode + artwork) is inherently
client-side, the two new "screens" are **server-rendered shells that `player.js` fills and drives
live**. This keeps the existing SSR/HTMX pattern (real routes, `hx-push-url`, back via
`history.back()`) while live playback state stays in the browser.

### Shared refactor: `playEpisode(this)`

Today play buttons call `playEpisode(id, url, title)`. Change the signature to
`playEpisode(el)` reading `data-*` attributes:

- `data-id`, `data-audio-url`, `data-title`, `data-artwork`, `data-podcast`

Apply at every site that renders a play affordance: `EpisodeItem`, `QueuePage` (`QueueRow`), the
new `EpisodeDetail`, and the new `NowPlaying`. `EpisodeDetailDto` already carries `podcastImage`
and `podcastName` on every episode, so artwork/podcast need no new data plumbing. `player.js`
stores the current episode's artwork + podcast in module state for the bar and Now Playing to read.

## Feature designs

### 1. Player bar (match Android `PlayerBar`)

`#player-bar` in `Layout.tsx` becomes:

```
[artwork 48px] [title / podcast (2 lines)] [⏪] [▶/⏸] [⏩]
[----------------- thin progress line -----------------]
```

- Drop the native `<audio controls>` chrome. Keep `<audio id="player-audio">` (no `controls`
  attribute); `player.js` already owns play/pause + `timeupdate`.
- Add `seekBack()` / `seekForward()` in `player.js`. Increments: **15s back / 30s forward.**
  (Android currently uses Media3 defaults 5s/15s; aligning Android is separate native follow-up.)
- The progress line is a `<div>` whose fill width is set on `timeupdate`.
- Clicking the bar **body** (not the transport buttons) opens Now Playing.

### 2. Now Playing — `GET /now-playing`

A Hono route returning a full-screen view that swaps `#content-container` (with `hx-push-url`), back
arrow → `history.back()`. The mini player-bar is hidden while this view is active (body class set by
the existing `updateNavActive` hook, extended to flag the now-playing path).

The view renders **shells** that `player.js` populates:

- large artwork `#np-artwork`, `#np-title`, `#np-podcast`
- a `<input type="range" id="np-scrubber">` (native drag = minimal JS)
- elapsed `#np-elapsed` / duration `#np-duration` labels
- transport: `⏪` `▶/⏸` `⏩`

`player.js` fills the shells on `htmx:afterSettle` from module state and keeps them live:
`timeupdate` → scrubber value + elapsed; play/pause → icon swap; `input` on scrubber →
`audio.currentTime = value`. If nothing is playing, render a "Nothing playing" empty state.

### 3. Episode detail — `GET /episodes/:id`

Route fetches `GET /api/episodes/:id` and renders a new `components/EpisodeDetail.tsx`:

```
← Back
[artwork] Episode Title
          Podcast · 3 days ago · 38 min
[▶ Play] [+ Queue] [✓ Played]
[--------- progress (if any) ---------]
Full show notes (expanded; <a> rewritten to target="_blank" rel="noopener noreferrer")
```

- Play → `playEpisode(this)` with full `data-*`.
- `+ Queue` → existing `hx-post="/queue/:id"` behavior.
- `✓ Played` → existing `togglePlayed` handler.
- Progress bar reuses `.episode-progress-bar` styling.

In `EpisodeItem.tsx`, make the **title/body** an `hx-get` link to `/episodes/:id`
(`hx-target="#content-container"`, `hx-swap="outerHTML"`, `hx-push-url="true"`). The existing
action buttons already live in a separate `.episode-actions` container, so taps don't collide with
row navigation. No change to Recent/Queue/PodcastDetail data flow.

### 4. Queue reorder — drag-and-drop, no library

`QueueRow` gets a `⋮⋮` drag handle and `draggable="true"`. A small vanilla handler scoped to
`#queue-list`:

- `dragstart` records the dragged row id; `dragover` allows drop and shows insertion position;
  `drop` reorders the DOM nodes.
- After drop: renumber the `.queue-position` labels client-side, then
  `fetch('/api/queue', { method:'PUT', headers:{'Content-Type':'application/json'},
  body: JSON.stringify({ episodeIds: [...] }) })`.
- On failure: refetch `/queue` fragment to resync.

Existing play + remove buttons stay. The queue badge logic is unchanged.

## File touch list

- `webapp/components/Layout.tsx` — player-bar redesign; now-playing-active body class hook.
- `webapp/static/js/player.js` — `playEpisode(this)` refactor; artwork/podcast state; seek
  (15/30); now-playing populate+drive; queue drag-and-drop.
- `webapp/static/css/style.css` — player bar, now-playing view, episode detail, drag handle.
- `webapp/components/NowPlaying.tsx` — **new** shell view.
- `webapp/components/EpisodeDetail.tsx` — **new** detail view.
- `webapp/components/EpisodeItem.tsx` — title becomes detail link; play button `data-*`.
- `webapp/components/QueuePage.tsx` — drag handle + `data-*` on play button.
- `webapp/server.tsx` — add `GET /now-playing` and `GET /episodes/:id` routes (HTMX
  fragment-vs-`Layout` handling like existing routes).

## Testing

The webapp has **no automated test suite** (tests live in the Kotlin modules). Verification is
manual smoke testing against a running backend (`bun run server.tsx` + Kotlin on :8100):

1. Play an episode → bar shows artwork, title, podcast, working ⏪/▶⏸/⏩ and progress line.
2. Click bar → Now Playing opens with artwork + live scrubber; scrub seeks; back returns.
3. Click an episode title → episode page with full notes; Play / +Queue / ✓Played work.
4. Reorder queue by dragging → order persists after reload; positions renumber.
5. Regression: Recent/Catalog/Settings/Add-podcast/OPML still work; queue badge still updates.

Standing up a `bun test` route harness is possible but is net-new infrastructure and is **out of
scope** for this effort.

## Non-goals / explicit exclusions

- No changes to the Kotlin backend or shared DTOs.
- No playback-speed control or sleep timer (absent from both apps; not parity).
- No pull-to-refresh (touch gesture; browser reload covers it).
- Android's 5s/15s seek increments are left as-is (native; separate follow-up if desired).
