# Phase 2 — Webapp: Queue and UX Completion

## Goal

Surface the data and functionality the backend already provides but the
webapp ignores: playback progress, episode dates, and the complete queue
system. Fix the rough edges in the existing flows.

## Constraints and observations

- `duration` in `EpisodeDetailDto` is a pre-formatted string ("1:23:45"),
  not a number. A progress bar needs a numeric value → `durationMs` must
  be added to the DTO.
- `GET /api/queue` returns episode IDs only. Displaying and auto-playing
  from the queue requires episode metadata (title, audioUrl, duration).
  A new application-layer use case and endpoint are needed before any
  queue UI can be built.
- `progressMs` and `publishedAt` are already in `EpisodeDetailDto` and
  passed through SSR — they just aren't rendered.
- `checkDescriptionOverflow` in `player.js` exists to hide the "Show more"
  button when a description is short. This can be moved server-side:
  render the expandable UI only when `description.length > 300`. Imprecise
  but eliminates client-side DOM measurement.

## Dependency order

Backend work must precede the queue UI. The UX quick wins are independent
and can be done in any order.

---

## Backend

### 1. Add `durationMs` to `EpisodeDetailDto`

`EpisodeDetailDto.duration` is a human-readable string. Add a parallel
`durationMs: Long?` field containing the raw millisecond value so the
webapp can compute progress percentages.

- [ ] Add `durationMs: Long?` to `EpisodeDetailDto` in `shared-models`
- [ ] Populate it in `episodeDetailDto()` in `PodcastApi.kt`
- [ ] Regenerate `webapp/generated/api.ts`

### 2. `findEpisodeById` on `PodcastCatalog`

The domain currently has no way to look up a single episode by ID.
This is the primitive needed by `GetQueueDetail`.

- [ ] Add `findEpisodeById(id: EpisodeId): Episode?` to `PodcastCatalog` port
- [ ] Implement with `SELECT * FROM episodes WHERE id = ?` in `SQLitePodcastCatalog`
- [ ] Add to `FakePodcastCatalog` (returns from in-memory map)

### 3. `GetQueueDetail` application use case

Mirrors `GetPodcastDetail`: cross-domain orchestration that composes the
queue port with episode lookups and playback state enrichment.

- [ ] Add `GetQueueDetail` use case in `application/usecase/`
  - Calls `GetQueue` → episode IDs
  - Calls `findEpisodeById` for each ID (episodes not in catalog are
    silently dropped — handles stale queue entries)
  - Calls `GetPlaybackStates` to enrich with played/progressMs
  - Returns `List<EpisodeWithPlayback>` in queue order
- [ ] Register `GetQueueDetail` in `ApplicationModule`
- [ ] Add `GET /api/queue/detail` route in `adapters/api/QueueApi.kt`
  returning `List<EpisodeDetailDto>`
- [ ] Add route to `Routes.kt`
- [ ] Write domain test for `GetQueueDetail`
- [ ] Write API test for `GET /api/queue/detail`

---

## Webapp — UX quick wins

### 4. Show playback progress on episode items

The episode card knows both `progressMs` and (after backend change) `durationMs`.
Render a thin progress bar at the bottom of the card. Only show it when
`progressMs > 0` and the episode is not already marked played.

- [ ] Add `durationMs` to the `Episode` TypeScript type (after regenerating api.ts)
- [ ] Add progress bar element to `EpisodeItem` component (CSS width:
  `calc(progressMs / durationMs * 100%)`)
- [ ] Style: 3px accent-colored bar at the bottom of the episode card,
  hidden when `progressMs === 0` or `played === true`

### 5. Show publish date on episode items

`publishedAt` is an ISO 8601 string. Format it as a human-readable date
(e.g. "12 Jan 2025") and show it alongside duration in `EpisodeRow`.

- [ ] Format `publishedAt` in `EpisodeItem` (locale string, no library needed)
- [ ] Add to `episode-extras` alongside duration badge

### 6. Fix the resume jump

`playEpisode()` currently sets `audio.src` and calls `audio.play()`
immediately, then seeks when the WebSocket state response arrives. The
user hears audio start from zero then jump.

Fix: send the WebSocket `get` request first, hold play until the state
response arrives (or a 300ms timeout elapses to avoid blocking on
connection delay), then set src, seek, and play atomically.

- [ ] Refactor `playEpisode()` to request state before starting audio
- [ ] Resolve via a one-shot `ws.onmessage` handler with a timeout fallback
- [ ] Keep existing play/pause toggle behaviour for re-clicking the
  current episode

### 7. Clear modal input on close

The RSS URL input retains the previous value when the modal is reopened.

- [ ] Add a `close` event listener on `#add-feed-modal` that calls
  `form.reset()` on the subscribe form

### 8. Loading indicator on podcast card navigation

No visual feedback while the server fetches podcast detail.

- [ ] Add `hx-indicator` to `PodcastCard` pointing to a global spinner
  or a per-card overlay

### 9. Eliminate `checkDescriptionOverflow` JS

Server already knows description content. Render the expandable toggle
only when `description.length > 300`. Removes client-side DOM measurement
and `htmx:afterSettle` hook.

- [ ] Move the short-description check to `EpisodeItem` component
- [ ] Remove `checkDescriptionOverflow` and its two event listeners from
  `player.js`

### 10. Lazy WebSocket connection

The WebSocket connects on every page load even if the user never plays
anything.

- [ ] Move WebSocket construction into `playEpisode()`, connecting on first
  play
- [ ] Reconnect if the socket has closed when play is triggered again

---

## Webapp — Queue UI

### 11. "Add to queue" button on episode items

- [ ] Add a queue button to `EpisodeItem` (distinct from the play button)
- [ ] On click: `POST /api/queue/{id}/last` via `fetch`
- [ ] Brief visual confirmation (button flash or toast) — no page swap needed

### 12. Queue page

- [ ] Add `GET /queue` route in `server.tsx` that fetches `GET /api/queue/detail`
- [ ] Build `QueuePage` component:
  - Empty state when queue is empty
  - Ordered list of episodes with title, duration, played badge, progress
  - Remove button per episode (`DELETE /api/queue/{episodeId}`)
  - "Play queue" button (plays the first episode and enables queue mode)
- [ ] Add queue link to the app header
- [ ] HTMX navigation (`hx-get`, `hx-push-url`) consistent with podcast pages
- [ ] Handle HTMX partial response

### 13. Auto-play next from queue

When an episode ends, check if it was the head of the queue. If so,
dequeue it and auto-play the next one.

- [ ] On `audio ended`: call `GET /api/queue/detail`
- [ ] If the finished episode is first in the returned list:
  - `DELETE /api/queue/{finishedId}`
  - Play the new first episode (title, audioUrl from queue detail response)
- [ ] If not in queue, do nothing (normal single-episode playback)

### 14. Show queue position in player bar

- [ ] When playing from queue, show "Queue · 3 remaining" in the player bar
- [ ] Update count after each auto-advance

---

## Definition of done

- All checklist items complete
- `./gradlew test` passes
- Manual test: add podcast, play episode with saved progress (resume works
  without jump), add episode to queue, navigate to queue page, play queue
  auto-advances to next episode, played badge appears server-side on reload
