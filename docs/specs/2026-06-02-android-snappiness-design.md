# Android Snappiness: Local-First Data + Instant Playback

**Date:** 2026-06-02
**Status:** Partially implemented (as of 2026-08-21). Built: §2 instant resume seek (via
DataStore rather than Room), §3 shared cache + downloads, §6 offline conflict resolution
(delivered by [`2026-07-31-offline-progress-sync-design.md`](2026-07-31-offline-progress-sync-design.md)).
Outstanding: §1 Room read-through cache (never started — no Room dependency in the app),
§4 auto-download the queue + prefetch-on-open (downloads are manual only), §5 backend
ownership (`GET /api/resume`, server-side dequeue on `ended`, `completionPercent` on the
episode DTO).

## Problem

The Android app feels sluggish next to Pocket Casts. Two concrete complaints:

1. **"Things aren't there when I click."** Every screen is a live network fetch. `PodcastRepositoryImpl`, `EpisodeRepositoryImpl`, `QueueRepositoryImpl` all call Retrofit → the Pi directly, with **no local persistence**. There is no Room DB and no disk cache, so opening Recent, a podcast, or the queue always shows a spinner while a round-trip to the Pi (often over Tailscale) completes.

2. **"Clicking play buffers instead of starting."** `PlaybackService.playableItem()` hands ExoPlayer the remote `audioUrl` and `prepare()` fetches it on demand — nothing is pre-buffered or downloaded. Worse, on play the service sends a WebSocket `{"type":"get"}` and only seeks to the resume position *after* the Pi round-trips, so even content that is already local stalls or starts at 0 then jumps.

Usage context: phone is used **both at home (low latency) and out (Tailscale, occasional offline)**, so offline browsing matters. Mobile data is unlimited; storage is not.

## Goals

- Screens render **instantly from local state**, never blocking on the network. Offline browsing works.
- **Tap Play starts immediately** — from a downloaded/cached file when available, and at the correct resume position without waiting on the Pi.
- Queued episodes are available offline.

## Source of Truth

**The Pi server is the absolute source of truth.** Room is strictly a **read-through cache / local mirror**, never a competing authority. The invariant: **the server always wins a reconcile; local rows never override server data.** Every `refresh()` overwrites local rows with the server's response. The only consequence of the cache is that reads may be *stale* between syncs (or offline) — stale is not a second source of truth, because the moment the server is reachable it overwrites.

## Logic Placement

**Domain decisions live on the Pi and are exposed via the API. Clients hold only three things: device-IO (player, downloads, local storage), presentation, and dumb cache mirroring.** No business rule is reimplemented per-client.

The distinction that matters:

- **Domain logic** (what counts as played, how the queue advances, what the resume target is, completion %, feed refresh policy) → **server**, behind the API. Both Android and the future webapp render the answer; neither computes it.
- **Device-inherent concerns** (caching to disk, audio playback, downloading files, offline availability) → **client**, because the backend physically cannot do them on the device. These are infrastructure, not domain logic, and are expected to live in each app.

This guardrail is the reason the webapp gets parity cheaply later: it inherits every domain decision from the same endpoints instead of re-deriving them in TypeScript.

As part of this work we **pull the domain logic currently leaking into the Android `PlaybackService` back onto the Pi** — see [Backend ownership of domain decisions](#5-backend-ownership-of-domain-decisions).

## Non-Goals (v1)

- **Offline writes — mostly.** Manual mark-played and queue edits (add/reorder) require the Pi to be reachable. The **one exception** is auto-advance on finish (mark-played + dequeue), which works offline via a minimal, conflict-free outbox ([§6](#6-offline-listening--cross-device-conflict-resolution)). The full offline-write build (manual edits, mid-episode progress) is in [Deferred Work](#deferred-work).

## Architecture

### 1. Data layer — Room as a read-through cache

Add a `CastDatabase` (Room) with entities for podcasts, episodes, and the queue. DAOs expose `Flow<…>` reads. Room mirrors the server; it is never authoritative (see [Source of Truth](#source-of-truth)).

Repositories flip from "call the API" to **local-first stale-while-revalidate** for reads, and **write-through** for mutations:

- **Reads:** expose `Flow` backed by Room. ViewModels collect these and render immediately from cache.
- **`suspend fun refresh()`:** fetches from the Pi via the existing `CastApiService` and upserts into Room (server overwrites local). Triggered on screen-open, pull-to-refresh, and the background `RefreshFeedsWorker`.
- **Writes (mark-played, queue add/remove, add-podcast) — write-through:** call the Pi **first**, then update Room from the server's response. Room is never ahead of the server; there is no transient local-ahead state. Requires connectivity (v1). The mutating tap is slightly less instant than an optimistic update, but reads and playback stay instant. (Optimistic-then-reconcile is a possible future change — see [Deferred Work](#deferred-work).)

This is the single most impactful change and also the most invasive: **five ViewModels** (`CatalogViewModel`, `PodcastDetailViewModel`, `EpisodeDetailViewModel`, `RecentViewModel`, `QueueViewModel`) flip from `suspend`-load-into-`UiState` to collecting a `Flow`. This conversion is where regressions hide, so it is the first reviewable slice.

The existing `RefreshFeedsWorker` becomes the background sync trigger that keeps Room warm.

### 2. Instant playback — seek to local progress first

**The resume position must not be on the network critical path.** On play / media-item transition, seek to the **Room-cached `progressMs` immediately**, then let the existing WebSocket `{"type":"get"}` reconcile and correct if the server is ahead. This removes the Pi from the perceived play path and is the direct fix for "clicking play should start right away." The WebSocket progress-sync mechanism (`PlaybackService` `states.collect`, `startProgressSync`, `onPlayWhenReadyChanged`) is otherwise unchanged and stays server-authoritative during playback.

Note the *value* is still the server's — Room only holds the last value the server gave us. The client decides *to seek now instead of waiting*, which is a device-IO/latency concern; it does not decide *what* the position is.

### 3. Shared Media3 cache + downloads

- **One shared `Cache`** between `DownloadManager` (writes) and `CacheDataSource` (reads), so a downloaded episode plays from disk automatically — no URI-swapping logic in `playableItem()`.
- **Stable `customCacheKey` = episode id** on both `MediaItem.Builder().setCustomCacheKey(...)` and `DownloadRequest.Builder(...).setCustomCacheKey(...)`. Podcast audio URLs routinely carry redirect/analytics wrappers (pdst.fm, Megaphone, Art19) and signed query params that vary per request; the default URL-derived cache key would miss and silently re-stream. Episode-id keying is what makes "downloaded → plays offline" actually hold.
- **Eviction policy — downloads must never be evicted by streaming churn.** A single cache with a `LeastRecentlyUsedCacheEvictor` cannot tell download spans from stream-cached spans, so transient streaming could silently evict the queue you downloaded for offline. Use a **two-cache split**: a download cache with `NoOpCacheEvictor` (managed by `DownloadManager`) that the player reads from, plus a bounded LRU cache for transient streaming of non-downloaded episodes. **The exact Media3 wiring (how the player consults the download cache first, then the streaming cache) must be verified against the Media3 demo/docs when implementing — not guessed.**

### 4. Downloads policy

- **Auto-download the queue.** Adding an episode to the queue enqueues a `DownloadRequest`; removing it or marking it played removes the download to reclaim storage. A component observes the queue (Room `Flow`) and reconciles downloads against it.
- **Prefetch on episode-detail-open.** Opening an episode's detail kicks off a background download so a subsequent tap is instant (data is unlimited).
- **Speculative-download thrash guard.** Detail-open downloads are *speculative* — browsing ten episodes must not leave ten orphaned files, nor must they fight the queue-driven cleanup. Speculative downloads get their **own reap rule** (e.g. capped count / TTL / evicted when not promoted to the queue), distinct from queue-download cleanup.

### 5. Backend ownership of domain decisions

Domain logic that currently lives in the Android `PlaybackService` moves to the Pi (new use cases in `core/` exposed through the web API adapter), so both clients consume it instead of re-deriving it. Each client keeps only the device-IO that consumes the answer.

| Decision | Today (Android-side) | Moves to (Pi) | Client keeps |
|----------|---------------------|---------------|--------------|
| **Resume target** ("continue listening") | `lastUnfinishedEpisode()` + device-local `LAST_EPISODE_ID` in DataStore, filtered by `!played` | `GET /api/resume` → most-recently-progressed unfinished episode + position, via `ORDER BY updated_at DESC WHERE played = 0`. The `playback_state.updated_at` column **already exists — no schema migration**. (Cross-device correctness once offline progress syncs depends on the listen-time timestamp in [§6](#6-offline-listening--cross-device-conflict-resolution).) | `refresh()` stores the result in **Room**; `onPlaybackResumption` / Android Auto recent root / widget read **Room** (a cheap local read). It must **not** be a live Pi fetch — `onGetLibraryRoot` blocks Auto's main thread and a network call there risks an ANR (the existing recent-root comment documents exactly this). |
| **Queue advance on finish** | `playNextInQueue()` removes the finished item and starts the next | On the existing `{"type":"ended"}` signal the server marks the episode played **and** dequeues it (idempotently). "Next" is just the queue's first item (server-owned order). Offline, the advance runs locally and the played+dequeue replays via the minimal outbox ([§6](#6-offline-listening--cross-device-conflict-resolution)). | Plays whatever the refreshed queue reports as first; no client-side dequeue *rule*. |
| **Completion status / %** | `completionExtras()` computes `progressMs / durationMs` | Server provides `completionPercent` + status on the episode DTO. | Maps the server field to Android Auto's `EXTRAS_KEY_COMPLETION_*`. |

**Resume semantics (decided):** "continue listening" becomes *most recent unfinished episode across all devices* (the webapp counts too), replacing today's *last episode played on this specific phone*. True cross-device continue; deletes the device-local `LAST_EPISODE_ID` hack.

### 6. Offline listening & cross-device conflict resolution

**Offline auto-advance — supported in v1 via a minimal outbox.** When a downloaded episode finishes offline, the player advances to the next downloaded queue item locally, and the resulting *mark-played + dequeue* is queued in a **minimal outbox** that replays to the Pi on reconnect. This is the one slice of offline-write pulled forward from [Deferred Work](#deferred-work), because offline auto-advance is core to "downloads on a plane." It is safe to pull forward because mark-played and dequeue are **commutative and idempotent**: two devices replaying them in any order — or replaying the same op twice — converge to the same server state, so **no conflict policy is needed for them**. The server must make `ended` idempotent (dequeuing an already-removed id and re-marking an already-played episode are no-ops).

**Mid-episode progress offline is NOT synced in v1** (see Deferred item 3). If you listen offline without *finishing* an episode, that partial progress is discarded on reconnect and the server keeps its pre-offline value. Accepted v1 limitation.

**Conflict policy for offline progress (deferred build, specified now).** Two devices editing the same episode's progress offline is a genuine concurrent write. Resolution: each progress write carries the **device's listen-time timestamp** (captured when the position was reached, not when it flushes), and the server keeps the **most-recent-timestamp write** — last-writer-wins by listen time. Worked example: server starts at progress 10:00; device 1 listens to 20:00 (ts 10:10); device 2 seeks to 19:50 and listens to 29:50 (ts 10:20); both flush at 10:30 → server keeps **29:50**, *independent of flush order*. Most-recent-wins (not furthest-position) is deliberate — it honors a rewind as the latest intent. This is the same `updated_at` timestamp that powers cross-device resume, so one mechanism serves both.

**Assumption: device clocks are reasonably NTP-synced.** Listen-time LWW trusts device clocks; a badly skewed clock could let an older session win. Acceptable for a single-user, few-device setup; logical/hybrid clocks are explicitly out of scope.

**Current server gap (addressed with the offline-progress build, not v1).** The server today does **not** resolve the concurrent case correctly:

- `UpdateProgress` stamps `clock.instant()` at *processing* time, not listen time — so both writes flushed at 10:30 get ~10:30 and the ordering is lost. The `update` WS message carries only `progressMs`, no client timestamp.
- `SQLitePlaybackState.updateProgress` is an **unconditional overwrite** (`ON CONFLICT DO UPDATE SET progress_ms = excluded.progress_ms`) — final value = whichever write *arrives last*, which can be the stale device.

The fix — **no schema migration**, since `playback_state.updated_at` and `PlaybackState.updatedAt` already exist: (a) carry the client listen-time timestamp in `update` and use it in `UpdateProgress` instead of the server clock; (b) make the upsert a **conditional LWW** that overwrites only when `excluded.updated_at > updated_at`. This is correct for v1's online-only scope as-is (server-receive-time ≈ listen-time when online, single active stream), so the change rides with the deferred offline-progress work.

## Data Flow

```
Screen open ──► ViewModel collects repo.Flow ──► renders Room data INSTANTLY
                      │
                      └► repo.refresh() ──► CastApiService ──► Pi ──► upsert Room ──► Flow re-emits

Add to queue ──► Pi push (write-through) ──► update Room from server response ──► UI updates
                      │
                      └► queue observer ──► DownloadManager.enqueue(episode)

Tap Play ──► seek to Room progressMs IMMEDIATELY ──► play (from shared cache if downloaded)
                      │
                      └► WS {"type":"get"} ──► reconcile/correct position
```

## Landing Order

Felt snappiness lands first, even within the larger build.

1. **Backend: own the domain decisions** (`GET /api/resume`, server-side queue-advance on `ended`, completion-% on the DTO). Land first so the client slices consume server answers from the start rather than building on client-side logic we're about to delete. Pure backend + API tests, no app risk.
2. **Repos → `Flow` + `refresh()`, ViewModels collect, + seek-to-local-progress.** After this slice: no spinners on revisit, and Play starts instantly at the right position. (The invasive ViewModel flip — review carefully.)
3. **Room sync + offline browse.** Background sync via `RefreshFeedsWorker`; durable cold-start + offline reads.
4. **Shared cache + auto-download queue + prefetch-on-open.** True offline listening and instant play for queued/opened episodes.

## Testing

- **Backend (Kotest, per CLAUDE.md conventions):** domain tests for the resume-target use case (most-recently-progressed unfinished episode) and queue-advance-on-`ended` (marks played + dequeues); API tests for `GET /api/resume` and the completion-% field on the episode DTO. These are the new server-owned decisions and must be covered server-side, not in the app.
- **Repository tests:** Room-backed reads emit cached data without a network call; `refresh()` upserts and re-emits with server data overwriting local; write-through mutations update Room only after the API succeeds, and a failed API call leaves Room unchanged.
- **DAO tests:** in-memory Room, query/upsert/ordering for the queue.
- **Playback:** unit-test that the resume seek uses Room progress before any WS response (extract the seek decision so it is testable without a live player/session).
- **Cache key:** assert `MediaItem` and `DownloadRequest` carry the episode-id `customCacheKey`.
- Existing instrumented tests continue to cover Android Auto browse/playback.

## Deferred Work

These are explicitly out of v1 but **intended**:

1. **Optimistic writes.** Possibly switch mutations from write-through to optimistic-then-reconcile (Room updates instantly on the tap, server push reconciles, rollback on failure) for snappier mutating taps — accepting a brief, self-correcting window where local is ahead of the server. Deferred because the server is the absolute source of truth and write-through keeps zero divergence; revisit if the mutating tap feels slow.
2. **Full offline-write outbox (the "really do it" item).** Generalize the minimal auto-advance outbox ([§6](#6-offline-listening--cross-device-conflict-resolution)) to *all* mutations — manual mark-played, queue add/reorder — replaying to the Pi on reconnect. Unlike auto-advance's commutative ops, queue edits are positional and need the server to arbitrate ordering on replay.
3. **Offline progress persistence + the server LWW fix.** Persist playback `progressMs` locally while offline and sync it on reconnect, so resume is correct without the Pi. This is where the [§6 current-server gap](#6-offline-listening--cross-device-conflict-resolution) gets fixed: carry the client listen-time timestamp in `update` and make `SQLitePlaybackState.updateProgress` a conditional last-writer-wins keyed on `updated_at`. The conflict policy (most-recent-listen-wins) is already specified in §6.

The auto-advance outbox, in-memory stale-while-revalidate, and seek-to-local-progress in the v1 slices are what buy most of the *felt* snappiness — which is why those lead.
