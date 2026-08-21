# Android — Visual feedback on action button press

**Date:** 2026-06-03
**Status:** implemented

## Problem

Several action buttons in the Android app fire-and-forget with no confirmation,
so a tap feels like nothing happened. Reported pain points: "Save server",
"Add to queue", etc.

Note: every Material3 `IconButton`/`Button` already renders a ripple on tap, so
raw "the press registered" feedback technically exists. What is actually missing
is **confirmation that the action did something**. That is why the feedback must
be *semantic* (a checkmark / "Saved") rather than a generic press animation
(scale/bounce), which would not address the complaint.

## Scope

Feedback type: **visual only, fire-on-tap** (no haptics, no snackbar; does not
wait for the backend result). Decided with the user.

Buttons in scope:

| Button | Location | Feedback |
|--------|----------|----------|
| Add to queue | `EpisodeItem.kt` (shared: Recent + Podcast detail) and `EpisodeDetailScreen.kt` | `PlaylistAdd` icon morphs to a primary-tinted `Check` for ~1.2s |
| Save server URL | `SettingsScreen.kt` | Button label → "Saved ✓" for ~1.2s |
| Mark all played | `PodcastDetailScreen.kt` | Button label → "Done ✓" for ~1.2s |

Explicitly **out of scope** (already provide state-change feedback):
Play / Pause, seek, mark-played toggle (tint flips), remove-from-queue (row
disappears).

**Subscribe (Add podcast sheet)** is intentionally left as-is: the Add button
already shows a `CircularProgressIndicator` while loading, the sheet auto-closes
on success, and errors already render inline. Added feedback would either
conflict with the spinner or never be seen.

## Approach

Two small reusable helpers in `ui/components/`. Deliberately NOT a button
framework — three call sites only.

### 1. `rememberConfirmTrigger(durationMs: Long = 1200): Pair<Boolean, () -> Unit>`

The only shared logic. Holds a boolean that flips to `true` on trigger and
auto-resets after `durationMs` via a `LaunchedEffect(confirmed)`. Visual-only.

```kotlin
@Composable
fun rememberConfirmTrigger(durationMs: Long = 1200): Pair<Boolean, () -> Unit> {
    var confirmed by remember { mutableStateOf(false) }
    LaunchedEffect(confirmed) {
        if (confirmed) {
            delay(durationMs)
            confirmed = false
        }
    }
    return confirmed to { confirmed = true }
}
```

### 2. `ConfirmIconButton(icon, contentDescription, onClick, modifier)`

Wraps `IconButton`. On tap: runs `onClick()` then triggers. Uses `Crossfade`
between `icon` and a primary-tinted `Icons.Default.Check` driven by the
trigger's boolean. Replaces the add-to-queue `IconButton` in `EpisodeItem.kt`
and `EpisodeDetailScreen.kt`.

### 3. `ConfirmButton(text, confirmedText, onClick, enabled, modifier)`

Wraps Material3 `Button`. On tap: runs `onClick()` then triggers. Swaps the
label `text` → `confirmedText` while confirmed. Replaces the Save-server button
(`SettingsScreen.kt`, `confirmedText = "Saved ✓"`) and the Mark-all-played
button (`PodcastDetailScreen.kt`, `confirmedText = "Done ✓"`).

## Behavior & known limitations

- **Fire-on-tap, ViewModels untouched.** The confirmation is pure UI-local
  state; no result plumbing is threaded out of any ViewModel. This is the
  lighter design and matches the user's "regardless of backend" choice.
- **Offline checkmark can lie.** An offline *add to queue* still shows ✓ even
  though that write needs connectivity (see offline-outbox note). Accepted as a
  known limitation; revisit if/when the offline outbox lands. Save-server is a
  local DataStore write that never meaningfully fails, so it is unambiguous.

## Testing & verification

Builds and instrumented Compose tests run on a separate machine, so this design
does not lean on automated tests as proof of done. The logic is kept trivial
(one timer helper). Manual verification checklist for the user to eyeball:

1. Add-to-queue icon (episode row + episode detail) flips to a checkmark on tap,
   then reverts after ~1.2s.
2. "Save server URL" label shows "Saved ✓" briefly after tap.
3. "Mark all played" label shows "Done ✓" briefly after tap.
4. Repeated taps re-trigger cleanly (timer resets).
