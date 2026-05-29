# Cast Android ⇄ Webapp Parity Plan

Five verticals. Each is independently shippable; within a vertical, do steps in order.

Suggested order: **V1 → V2 → V3 → V4 → V5**. V1 first because reorder is currently broken (UI lies to the user). V4 and V5 are pure polish.

---

## V1 — Queue (most broken, ship first) ✓

**Goal:** Add-to-queue works from any episode row; reorder persists.

1. **Backend: `PUT /api/queue` reorder endpoint.**
   - Add `ReorderQueue(episodeIds: List<EpisodeId>)` use case in `core/src/main/kotlin/queue/core/usecase/`.
   - Extend `QueuePersistence` with `reorder(ids)`; implement in `SQLiteQueuePersistence` (single UPDATE of the position column inside a transaction).
   - Wire in `api/QueueApi.kt`:
     ```kotlin
     put {
         val ids = call.receive<List<String>>()
         reorderQueue(ids.map(::EpisodeId))
         call.respond(getQueueDetail().map(::episodeDetailDto))
     }
     ```
   - Tests: `QueueCoreTests` for use case, `QueueApiTest` for endpoint, `SQLiteQueuePersistenceIT` for ordering invariant.

2. **Android: persist reorder.**
   - Add `reorderQueue(episodeIds): List<EpisodeDetailDto>` to `CastApiService` + `QueueRepository`.
   - `QueueViewModel.reorder` (lines 48-53): keep optimistic local update, then PUT to server, rollback on failure.

3. **Android: add-to-queue button on `EpisodeItem`.**
   - Add a third `IconButton` (queue / playlist-add icon) in `EpisodeItem.kt` between played-toggle and play.
   - Thread through an `onAddToQueue` lambda. Wire from `RecentScreen`, `PodcastDetailScreen`.
   - New `addToQueue(id)` methods on `RecentViewModel` and `PodcastDetailViewModel`, calling existing `QueueRepository.addToQueue`.

4. **Webapp: nothing.** Already has both.

---

## V2 — Episode row content ✓

**Goal:** Android `EpisodeItem` shows the same info as the webapp row.

1. **Persisted progress bar.**
   - In `EpisodeItem.kt`, compute `staticProgress = episode.progressMs / episode.durationMs` when `!isCurrent && progressMs > 0`. Render `LinearProgressIndicator` with that value when not current. Existing live-progress logic stays for the current episode.

2. **Published date.**
   - Port `relativeTime` from `webapp/components/EpisodeItem.tsx:80-92` to a Kotlin helper in `cast.android.util` (use `kotlinx.datetime` or `java.time`).
   - Add `pubDate` to the meta `joinToString(" · ")` line in `EpisodeItem.kt:78`.

3. **Expandable description.**
   - Add `var expanded by remember { mutableStateOf(false) }`.
   - Below the row, if `episode.description.isNotBlank()`: a "Show more / Show less" `TextButton` that toggles expansion; render description as HTML via `AndroidView { TextView(...).apply { text = HtmlCompat.fromHtml(...) } }`.

4. **Webapp: nothing.**

---

## V3 — OPML import ✓

**Goal:** Both clients can import an OPML file. (Backend endpoint already exists.)

1. **Android: file picker + upload.**
   - Add `@Multipart importOpml(@Part file: MultipartBody.Part): Response<Unit>` to `CastApiService`.
   - In `AddPodcastSheet.kt`, add an "Import OPML" button below the URL field. Launch `ActivityResultContracts.GetContent("*/*")` with MIME hint `application/xml`.
   - In `CatalogViewModel`, new `importOpml(uri)` reads bytes via `contentResolver.openInputStream`, calls service, reloads catalog.

2. **Webapp: nothing.**

---

## V4 — Settings UI on webapp ✓

**Goal:** Webapp exposes the `hidePlayed` toggle.

1. **Add `/settings` route to `webapp/server.tsx`.**
   - GET fetches `${KOTLIN_API}/api/settings`, renders `<SettingsPage>`.
   - POST forwards form to `PUT /api/settings`.

2. **Add `webapp/components/SettingsPage.tsx`** with one labeled checkbox bound via HTMX (`hx-post`, `hx-swap="none"`).

3. **Add nav link** in `Layout.tsx` (gear icon in the header, or a fourth nav entry).

4. **Skip Server URL on webapp** — it's the `KOTLIN_API` env var, not user-configurable.

5. **Android: nothing.** Already has it.

---

## V5 — Player polish ✓

**Goal:** In-app controls + stability cleanup.

1. **`PlayerBar.kt`:** add `IconButton`s for `seekBack` / `seekForward` flanking play/pause, calling existing `PlayerViewModel.seekBack / seekForward`.

2. **`PlayerViewModel.playEpisode`:** drop the 1500ms `delay` fallback (lines 106-113). If `controller == null`, attach the call via `controllerFuture.addListener { ... }` so it fires the moment the service connects.

---

## Out of scope (intentional)

- Pull-to-refresh / skeletons / connectivity banner / Android Auto / background refresh — Android-only platform conveniences with no web analog.
- Webapp player bar redesign — native `<audio controls>` already handles seeking.
