# Android Snappiness: Local-First Data + Instant Playback

**Date:** 2026-06-02
**Status:** Approved design, pending implementation plan

## Problem

The Android app feels sluggish next to Pocket Casts. Two concrete complaints:

1. **"Things aren't there when I click."** Every screen is a live network fetch. `PodcastRepositoryImpl`, `EpisodeRepositoryImpl`, `QueueRepositoryImpl` all call Retrofit → the Pi directly, with **no local persistence**. There is no Room DB and no disk cache, so opening Recent, a podcast, or the queue always shows a spinner while a round-trip to the Pi (often over Tailscale) completes.

2. **"Clicking play buffers instead of starting."** `PlaybackService.playableItem()` hands ExoPlayer the remote `audioUrl` and `prepare()` fetches it on demand — nothing is pre-buffered or downloaded. Worse, on play the service sends a WebSocket `{"type":"get"}` and only seeks to the resume position *after* the Pi round-trips, so even content that is already local stalls or starts at 0 then jumps.

Usage context: phone is used **both at home (low latency) and out (Tailscale, occasional offline)**, so offline browsing matters. Mobile data is unlimited; storage is not.

## Goals

- Screens render **instantly from local state**, never blocking on the network. Offline browsing works.
- **Tap Play starts immediately** — from a downloaded/cached file when available, and at the correct resume position without waiting on the Pi.
- Queued episodes are available offline.

## Non-Goals (v1)

- **Offline writes.** Mark-played / queue edits require the Pi to be reachable. See [Deferred Work](#deferred-work) for the proper offline-write build, which we *do* intend to do.
- New backend changes. The Pi API stays as-is; this is all on-device.

## Architecture

### 1. Data layer — Room as the on-device source of truth

Add a `CastDatabase` (Room) with entities for podcasts, episodes, and the queue. DAOs expose `Flow<…>` reads.

Repositories flip from "call the API" to **local-first stale-while-revalidate**:

- **Reads:** expose `Flow` backed by Room. ViewModels collect these and render immediately from cache.
- **`suspend fun refresh()`:** fetches from the Pi via the existing `CastApiService` and upserts into Room. Triggered on screen-open, pull-to-refresh, and the background `RefreshFeedsWorker`.
- **Writes (mark-played, queue add/remove, add-podcast):** optimistic — update Room immediately so the UI reacts instantly, push to the Pi, and roll the local change back on failure. Requires connectivity (v1).

This is the single most impactful change and also the most invasive: **five ViewModels** (`CatalogViewModel`, `PodcastDetailViewModel`, `EpisodeDetailViewModel`, `RecentViewModel`, `QueueViewModel`) flip from `suspend`-load-into-`UiState` to collecting a `Flow`. This conversion is where regressions hide, so it is the first reviewable slice.

The existing `RefreshFeedsWorker` becomes the background sync trigger that keeps Room warm.

### 2. Instant playback — seek to local progress first

**The resume position must not be on the network critical path.** On play / media-item transition, seek to the **Room-cached `progressMs` immediately**, then let the existing WebSocket `{"type":"get"}` reconcile and correct if the server is ahead. This removes the Pi from the perceived play path and is the direct fix for "clicking play should start right away." The WebSocket progress-sync mechanism (`PlaybackService` `states.collect`, `startProgressSync`, `onPlayWhenReadyChanged`) is otherwise unchanged and stays server-authoritative during playback.

### 3. Shared Media3 cache + downloads

- **One shared `Cache`** between `DownloadManager` (writes) and `CacheDataSource` (reads), so a downloaded episode plays from disk automatically — no URI-swapping logic in `playableItem()`.
- **Stable `customCacheKey` = episode id** on both `MediaItem.Builder().setCustomCacheKey(...)` and `DownloadRequest.Builder(...).setCustomCacheKey(...)`. Podcast audio URLs routinely carry redirect/analytics wrappers (pdst.fm, Megaphone, Art19) and signed query params that vary per request; the default URL-derived cache key would miss and silently re-stream. Episode-id keying is what makes "downloaded → plays offline" actually hold.
- **Eviction policy — downloads must never be evicted by streaming churn.** A single cache with a `LeastRecentlyUsedCacheEvictor` cannot tell download spans from stream-cached spans, so transient streaming could silently evict the queue you downloaded for offline. Use a **two-cache split**: a download cache with `NoOpCacheEvictor` (managed by `DownloadManager`) that the player reads from, plus a bounded LRU cache for transient streaming of non-downloaded episodes. **The exact Media3 wiring (how the player consults the download cache first, then the streaming cache) must be verified against the Media3 demo/docs when implementing — not guessed.**

### 4. Downloads policy

- **Auto-download the queue.** Adding an episode to the queue enqueues a `DownloadRequest`; removing it or marking it played removes the download to reclaim storage. A component observes the queue (Room `Flow`) and reconciles downloads against it.
- **Prefetch on episode-detail-open.** Opening an episode's detail kicks off a background download so a subsequent tap is instant (data is unlimited).
- **Speculative-download thrash guard.** Detail-open downloads are *speculative* — browsing ten episodes must not leave ten orphaned files, nor must they fight the queue-driven cleanup. Speculative downloads get their **own reap rule** (e.g. capped count / TTL / evicted when not promoted to the queue), distinct from queue-download cleanup.

## Data Flow

```
Screen open ──► ViewModel collects repo.Flow ──► renders Room data INSTANTLY
                      │
                      └► repo.refresh() ──► CastApiService ──► Pi ──► upsert Room ──► Flow re-emits

Add to queue ──► optimistic Room write (UI updates) ──► Pi push (rollback on fail)
                      │
                      └► queue observer ──► DownloadManager.enqueue(episode)

Tap Play ──► seek to Room progressMs IMMEDIATELY ──► play (from shared cache if downloaded)
                      │
                      └► WS {"type":"get"} ──► reconcile/correct position
```

## Landing Order

Felt snappiness lands first, even within the larger build.

1. **Repos → `Flow` + `refresh()`, ViewModels collect, + seek-to-local-progress.** After this slice alone: no spinners on revisit, and Play starts instantly at the right position. (The invasive ViewModel flip — review carefully.)
2. **Room sync + offline browse.** Background sync via `RefreshFeedsWorker`; durable cold-start + offline reads.
3. **Shared cache + auto-download queue + prefetch-on-open.** True offline listening and instant play for queued/opened episodes.

## Testing

- **Repository tests:** Room-backed reads emit cached data without a network call; `refresh()` upserts and re-emits; optimistic writes roll back on API failure.
- **DAO tests:** in-memory Room, query/upsert/ordering for the queue.
- **Playback:** unit-test that the resume seek uses Room progress before any WS response (extract the seek decision so it is testable without a live player/session).
- **Cache key:** assert `MediaItem` and `DownloadRequest` carry the episode-id `customCacheKey`.
- Existing instrumented tests continue to cover Android Auto browse/playback.

## Deferred Work

These are explicitly out of v1 but **intended**:

1. **Offline-write outbox (the "really do it" item).** Mutations (mark-played, queue edits, progress) apply locally and queue into a pending-ops outbox that replays to the Pi on reconnect, with conflict reconciliation. This is what makes the app fully usable offline, not just browsable.
2. **Offline progress persistence.** Persist playback `progressMs` locally while offline so resume is correct without the Pi, feeding the outbox above.

These two, plus the in-memory stale-while-revalidate and seek-to-local-progress in slice 1, are what buy most of the *felt* snappiness — which is why slice 1 leads.
