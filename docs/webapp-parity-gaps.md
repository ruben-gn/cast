# Webapp ↔ Android parity: remaining gaps

**Date:** 2026-08-21
**Status:** inventory, nothing started
**Scope:** non-native features present on Android and missing from `webapp/`

Round 1 of parity was closed by [`2026-06-03-webapp-parity-design.md`](specs/2026-06-03-webapp-parity-design.md),
which brought over Now Playing, episode detail, queue drag-reorder and the player bar. Everything
below has landed on Android *since* that spec.

The webapp has **no automated test suite** — tests live in the Kotlin modules. Verification for
anything here is manual smoke testing against a running backend, same as round 1.

---

## 1. Series grouping

The biggest gap, and the only one with backend work already paid for.

**Android has:** `groupIntoRows` collapses consecutive episodes sharing a
`(podcastId, seriesName)` key into one `RecentRow.Series`; single episodes stay
`RecentRow.Single`. `SeriesStackRow` renders the collapsed stack with a Series kicker, an
expand chevron, a rail under expanded children, and a ring on the queue pill. Long-press
opens group / ungroup dialogs. Expansion state (`expandedSeries`) is per-session UI state,
not persisted. `guessSeriesName` proposes a name from season/episode markers, falling back
to shared title prefixes.

**Webapp has:** nothing. `RecentPage.tsx` maps episodes straight to `EpisodeItem` — a flat list.

**Already on the server**, so no backend work:
- `EpisodeDetailDto.seriesName` is populated on the recent feed, episode detail *and* queue
  endpoints (`fdd6336`)
- `POST /api/podcasts/{id}/series` with `CreateSeriesRuleRequest { name }`
- `DELETE /api/podcasts/{id}/series?name=…`
- rules are cleared when a podcast is deleted (`177b332`)

**Notes for implementing:**
- `groupIntoRows` is pure and has no Android dependencies (`RecentRows.kt`, covered by
  `RecentRowsTest.kt`) — port the algorithm rather than reinventing the grouping rules,
  or the two clients will disagree about what counts as a series.
- Expansion is per-session on Android. In an SSR/HTMX app the cheap equivalent is a
  `<details>` element, which needs no state on the server and survives nothing — matching
  Android's behaviour by accident.
- Group/ungroup are the only actions needing new webapp routes; both are podcast-scoped, not
  episode-scoped, which is easy to get wrong from the UI (you group *the podcast's* series
  rule from an episode row).

## 2. Catalog: sections, sort, view toggle

**Android has** (per [`2026-06-09-catalog-view-toggle-sort-design.md`](specs/2026-06-09-catalog-view-toggle-sort-design.md)):
- podcasts split into **Listening** / **Not listening** sections, the second only rendered
  when non-empty
- sort by **Name** (`name.lowercase()`) or **Recently updated** (`latestEpisodeAt`
  descending; lexicographic is correct for ISO-8601), applied independently within each
  section, defaulting to Name
- a **grid ↔ list** view toggle

**Webapp has:** `PodcastList.tsx` renders one flat `.podcast-grid` of every podcast, no
sections, no sort, no toggle. Listening state is only a per-card badge.

**Notes:** all three are pure client-side derivations on Android — `PodcastSummaryDto`
already carries `listening`, `name` and `latestEpisodeAt`. No backend work. The open
question is whether the webapp sorts server-side in the Hono route (simplest for SSR, but a
full round-trip per toggle) or client-side after render (no round-trip, but duplicates the
sort logic). Worth deciding before starting.

## 3. Loading states

**Android has:** `Shimmer.kt`, used by both `RecentScreen` and `CatalogScreen`
(`RecentScreenSkeleton`).

**Webapp has:** a single 3px `.nav-spinner` progress line at the top of the page. Nothing
content-shaped.

**Notes:** HTMX makes this awkward in a way Compose doesn't — the fragment arrives fully
rendered, so a skeleton has to be shown by the *initiating* page during the request, via
`hx-indicator` on a skeleton block rather than the thin bar. Low value versus the two gaps
above; listed for completeness.

## 4. Mobile layout

**Webapp is desktop-first.** There is a `@media (max-width: 600px)` block in `style.css`, but
it adapts by *shrinking*: the logo is hidden, nav links drop to 13px, the "Add podcast" label
disappears. There is no bottom nav, and tap targets stay mouse-sized.

Android is obviously phone-shaped (`BottomNavBar.kt`). Since the webapp is the way the app
gets used on a desktop *and* on any non-Android device, this is a real gap rather than a
cosmetic one — but it is a layout rework, independent of both the palette and the feature
gaps above.

---

## Explicitly not gaps

Native-only, and out of scope by the same reasoning as the round-1 spec:

- downloads (`DownloadedEpisodesScreen`, `DownloadsViewModel`)
- offline progress outbox (`2026-07-31-offline-progress-sync-design.md`)
- Android Auto, home-screen widget, media-session / lock-screen controls, playback resumption
- connectivity awareness (`OfflineBanner`), background feed refresh, server-URL config
- pull-to-refresh — a touch gesture; browser reload covers it

Already at parity, for the avoidance of doubt: hide-played and recent-listening-only settings,
per-podcast listening toggle, episode detail, Now Playing, queue drag-reorder, player bar
transport, and — as of `f8852c0` — the Linen Paper palette with an Ember dark mode.
