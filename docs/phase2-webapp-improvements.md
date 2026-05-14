# Phase 2 — Webapp UX Improvements

## Backend prerequisites

- [ ] Add `durationMs: Long?` to `EpisodeDetailDto` in `shared-models`
- [ ] Populate it in `PodcastApi.kt` from `episode.duration`
- [ ] Regenerate `webapp/generated/api.ts` (run `./gradlew generateTypeScriptTypes` or equivalent)

## UX improvements

- [ ] **Progress bar on episode list** — render a thin `<progress>` element below each episode row using `playbackState.progressMs / durationMs`; show nothing when both are zero
- [ ] **Publish date** — display `episode.publishedAt` formatted as relative time (e.g. "3 days ago") next to the episode title; no JS required, compute on the server in `server.tsx`
- [ ] **Fix resume jump** — the WebSocket `get` response races the `<audio>` element `canplay` event; set `currentTime` inside the `canplay` listener instead of immediately on `get` response to eliminate the skip-to-zero-then-jump artefact
- [ ] **Clear modal on close** — when the episode detail modal is dismissed, reset the inner `#content-container` so stale content is not visible on next open; drive this with an HTMX `hx-on:htmx:after-request` swap or a small `htmx.on` listener
- [ ] **Loading indicator on card navigation** — add `hx-indicator` to the podcast card link so a CSS spinner is visible while the detail fragment loads; no extra JS needed
- [ ] **Eliminate `checkDescriptionOverflow` JS** — replace the JS truncation with a CSS-only approach: `display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden`; the "Show more" toggle can be driven by a `<details>`/`<summary>` element instead
- [ ] **Lazy WebSocket connection** — open the playback WebSocket only when the user starts playing an episode, not on page load; close it again when playback ends to avoid keeping a connection alive on every page
