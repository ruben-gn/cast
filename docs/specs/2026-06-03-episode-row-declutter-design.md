# Episode Row Declutter — Design

**Date:** 2026-06-03
**Status:** implemented
**Scope:** Android app only (`android/`)

## Problem

The episode row (`EpisodeItem`, shared by the **Recent** and **Podcast detail** screens)
carries three trailing buttons on every row: mark-played (✓), add-to-queue (☰+), and
play (▶). They compete with the title for horizontal space, narrow the text, and repeat
on every row.

Usage reality (single user):
- **Play** is the only primary action — wanted instantly on every row.
- **Add to queue** is used sometimes; not important.
- **Mark played** is rare — used when an episode was heard through another medium, or to
  tick it off. It is *not* a "hide" mechanism.

## Goal

Strip the row to a single Play/Pause button and move the two secondary actions behind a
**long-press bottom sheet**, without losing discoverability.

## Why long-press (and not an overflow ⋮ icon or swipe)

The usual downside of long-press — "how would the user know it's there?" — does not apply
here: **tapping a row already opens the Episode detail screen**, which keeps every action
(play, queue, mark-played) as visible buttons. So every action is always reachable; the
long-press sheet is purely a fast path. An overflow ⋮ icon would spend permanent row space
on rarely-used actions that are already reachable by tapping. Swipe would hide the actions
entirely and would collide with the swipe-to-delete gesture already used on the Queue
screen. Long-press is also the same gesture Android uses to enter multiselect mode, so this
choice does not foreclose a future multiselect feature.

## Design

### The row (`EpisodeItem`)

Layout becomes: thumbnail · (title + subtitle, full remaining width) · **one Play/Pause
button** · progress bar below. The mark-played and add-to-queue `IconButton`s are removed
from the trailing `Row`.

Interaction:
- **Tap** the row → existing behavior: navigate to Episode detail (`onClick`). Unchanged.
- **Long-press** the row → opens a `ModalBottomSheet` scoped to that episode.

Implement the tap + long-press with `Modifier.combinedClickable(onClick = …, onLongClick = …)`
replacing the current `clickable`. The Play button keeps its own `onClick` (it must not
trigger row tap/long-press).

### The bottom sheet

A Material3 `ModalBottomSheet` showing, as full-width list items:

1. **Add to queue** — invokes the existing `onAddToQueue`.
2. **Mark played / Mark unplayed** — label and icon track `episode.played`; invokes the
   existing `onTogglePlayed`. Reuse the optimistic local-toggle behavior already in
   `EpisodeItem`.
3. **Go to podcast** — *conditional*. Shown only when an `onGoToPodcast` handler is provided
   (i.e. on Recent, not on Podcast detail) and `episode.podcastId != null`. Navigates to
   `PodcastDetail(podcastId)`.

The sheet has a small header line with the episode title for context. Selecting any action
dismisses the sheet.

Sheet open/closed state is local to `EpisodeItem` (`var sheetOpen by remember { … }`); no
screen-level hoisting is needed because each row owns its own sheet.

### Component signature

`EpisodeItem` keeps its current parameters (`onPlay`, `onClick`, `onTogglePlayed`,
`onAddToQueue`) and gains one optional parameter:

```kotlin
onGoToPodcast: (() -> Unit)? = null
```

- **RecentScreen** passes `onGoToPodcast = { episode.podcastId?.let { navController.navigate(PodcastDetail(it)) } }`.
- **PodcastDetailScreen** passes `onGoToPodcast = null` (already on the podcast).

`podcastId` is confirmed populated by the server `/recent` endpoint
(`core/.../api/EpisodeApi.kt`), so "Go to podcast" is always valid where it is shown.

### Out of scope / untouched

- **Queue screen** — its row is a separate composable (`QueueEpisodeRow`) with drag-handle
  and swipe-to-delete; not affected.
- **Episode detail screen** — keeps its visible action buttons; this is the discoverability
  backstop and must remain.
- **Catalog screen** — unrelated.

### Parked for a later, separate spec

- **"Actively listening" podcast flag** — a podcast-level property that hides a podcast's
  episodes from Recent while still tracking them. Its own feature (data model + Recent
  filter + a toggle, likely in the Podcast detail top bar). Not part of this work.
- **Episode multiselect** — possible future; the long-press gesture leaves room for it.

## Testing

- Existing `EpisodeItem` / screen tests updated for the new layout (no inline secondary
  buttons; Play button present).
- Verify long-press opens the sheet and each sheet action invokes the correct callback.
- Verify "Go to podcast" is absent on Podcast detail and present on Recent.
- Manual check on device: tap → detail, long-press → sheet, play button does not trigger
  the sheet.
