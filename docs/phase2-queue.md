# Phase 2 — Queue Implementation

## Backend

### Domain layer

- [ ] Add `findEpisodeById(id: EpisodeId): Episode?` to `PodcastCatalog` port interface
- [ ] Implement it in `SQLitePodcastCatalog`: `SELECT * FROM episodes WHERE id = ?`
- [ ] Add the method to `FakePodcastCatalog` (used by tests): return from in-memory map

### Application layer

- [ ] Create `GetQueueDetail` use case in `application/`
  - Calls `queue.get()` to fetch the ordered `Queue`
  - Calls `catalog.findEpisodeById(id)` for each entry (parallel with `coroutineScope { map { async { } } }`)
  - Calls `playback.get(id)` for each episode
  - Returns `List<EpisodeWithPlayback>` in queue order, dropping any IDs that resolve to `null`

### API layer

- [ ] Replace `GET /api/queue` response with `List<EpisodeDetailDto>` (backed by `GetQueueDetail`); drop the ID-only response — no client ever needs IDs without the episode data
- [ ] `POST /api/queue` and `DELETE /api/queue/{episodeId}` return the updated `List<EpisodeDetailDto>` so callers never need a follow-up fetch after a mutation
- [ ] Write API tests for the updated GET, POST, and DELETE routes

## Frontend

- [ ] **Add-to-queue button** — add an "Add to queue" button on the episode detail view; `hx-post="/api/queue"` with the episode ID; swap the queue count in the player bar on success
- [ ] **Queue page** — new route `/queue` in `server.tsx`; `QueuePage` component fetches `GET /api/queue` and renders an ordered list of episode rows (same row component as the podcast detail view, reused)
- [ ] **Reorder / remove** — each queue row has a remove button (`hx-delete="/api/queue/{id}"`, `hx-target` the list); mutation response is the updated list, so HTMX can swap it in without a second request; drag-to-reorder can be deferred
- [ ] **Auto-play next** — when the `ended` WebSocket event fires, call `GET /api/queue`, take the first entry, remove it from the queue, and start playback; server decides the next track, client just follows
- [ ] **Queue count in player bar** — a small badge showing how many episodes remain; updated via HTMX `hx-trigger="every 30s"` or pushed via WebSocket alongside playback state updates
