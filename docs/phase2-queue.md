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

- [ ] Add `GET /api/queue/detail` route in `QueueApi.kt`
  - Resolves `GetQueueDetail` from DI
  - Returns `List<EpisodeDetailDto>` (reuse the same DTO as the podcast detail endpoint)
- [ ] Add `DELETE /api/queue/{episodeId}` route — remove a single entry and return updated queue detail
- [ ] Write API tests for `GetQueueDetail` and the delete route

## Frontend

- [ ] **Add-to-queue button** — add an "Add to queue" button on the episode detail view; `hx-post="/api/queue"` with the episode ID; swap the queue count in the player bar on success
- [ ] **Queue page** — new route `/queue` in `server.tsx`; `QueuePage` component fetches `GET /api/queue/detail` and renders an ordered list of episode rows (same row component as the podcast detail view, reused)
- [ ] **Reorder / remove** — each queue row has a remove button (`hx-delete="/api/queue/{id}"`, `hx-target` the list); drag-to-reorder can be deferred
- [ ] **Auto-play next** — when the `ended` WebSocket event fires, call `GET /api/queue/detail`, take the first entry if the current episode is at the head of the queue, remove it from the queue, and start playback; server decides the next track, client just follows
- [ ] **Queue count in player bar** — a small badge showing how many episodes remain; updated via HTMX `hx-trigger="every 30s"` or pushed via WebSocket alongside playback state updates
