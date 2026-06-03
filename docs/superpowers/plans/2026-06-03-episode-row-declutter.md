# Episode Row Declutter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Strip the shared Android episode row to a single Play/Pause button and move add-to-queue + mark-played behind a long-press bottom sheet.

**Architecture:** `EpisodeItem` (the Compose row used by both Recent and Podcast detail) drops its two trailing secondary `IconButton`s. A normal tap still opens Episode detail (the always-visible backstop for every action). A long-press opens a Material3 `ModalBottomSheet` whose actions invoke the row's existing callbacks. A new optional `onGoToPodcast` callback adds a "Go to podcast" sheet item that appears only on Recent.

**Tech Stack:** Kotlin, Jetpack Compose (BOM 2026.05.00), Material3 (`ModalBottomSheet`, `combinedClickable`), Hilt, Navigation-Compose.

---

## Testing approach (read first)

This is a presentational change. The codebase has **no existing Compose UI tests** (the
suite is JVM unit tests for ViewModels/repositories), and `EpisodeItem` reads a
`LocalPlayerViewModel` composition-local that must be stubbed to render in isolation.
Therefore each implementation task is verified by **building and running on a device**, which
per project convention happens **on the Mac, not the Pi** — the agent edits files; the user
runs `./gradlew`. An optional instrumented-test task (Task 4) is included for anyone who
wants regression coverage.

> **Build/verify commands in this plan are run by the user on the Mac.** The agent never runs
> `./gradlew`. After edits, ask the user to run the quoted command and report the result.

## File Structure

- **Modify** `android/app/src/main/kotlin/cast/android/ui/components/EpisodeItem.kt`
  — restructure the row to Play-only, add long-press → `ModalBottomSheet`, add the
  `onGoToPodcast` parameter, and add two small private composables (`EpisodeActionsSheet`,
  `SheetAction`) in the same file (they change together with the row).
- **Modify** `android/app/src/main/kotlin/cast/android/ui/screens/RecentScreen.kt`
  — pass `onGoToPodcast` so the sheet shows "Go to podcast".
- **Unchanged** `PodcastDetailScreen.kt` (omits `onGoToPodcast`, so the item defaults to
  hiding "Go to podcast"), `EpisodeDetailScreen.kt`, `QueueScreen.kt`, `CatalogScreen.kt`.
- **Optional, create** `android/app/src/androidTest/kotlin/cast/android/ui/components/EpisodeItemTest.kt`.

---

### Task 1: Rewrite `EpisodeItem` — Play-only row + long-press sheet

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/components/EpisodeItem.kt`

- [ ] **Step 1: Replace the entire file with the version below**

The row is restructured so Play sits centered at the trailing edge; the two secondary
`IconButton`s are gone. `combinedClickable` provides tap (→ detail) and long-press (→ sheet).
The sheet and its rows are private composables in the same file. The optimistic `played`
local state now drives the sheet's mark-played label instead of an inline icon.

```kotlin
package cast.android.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.util.relativeTime
import cast.api.EpisodeDetailDto
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeItem(
    episode: EpisodeDetailDto,
    onPlay: () -> Unit,
    onTogglePlayed: ((Boolean) -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onGoToPodcast: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val playerVm = LocalPlayerViewModel.current
    val currentMediaItem by playerVm.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying by playerVm.isPlaying.collectAsStateWithLifecycle()
    val position by playerVm.position.collectAsStateWithLifecycle()
    val duration by playerVm.duration.collectAsStateWithLifecycle()

    val lastKnownProgress by playerVm.lastKnownProgress.collectAsStateWithLifecycle()

    val isCurrent = currentMediaItem?.mediaId == episode.id

    var played by remember(episode.id, episode.played) { mutableStateOf(episode.played) }
    var showSheet by remember(episode.id) { mutableStateOf(false) }

    val hasSheetActions = onTogglePlayed != null || onAddToQueue != null || onGoToPodcast != null

    val savedProgress: Float? = when {
        played -> null
        !isCurrent && lastKnownProgress[episode.id]?.second.let { it != null && it > 0 } -> {
            val (pos, dur) = lastKnownProgress[episode.id]!!
            pos.toFloat() / dur.toFloat()
        }
        episode.progressMs > 0 && (episode.durationMs ?: 0L) > 0L ->
            episode.progressMs.toFloat() / episode.durationMs!!.toFloat()
        else -> null
    }

    val progress = if (isCurrent && duration > 0) position.toFloat() / duration.toFloat()
                   else if (isCurrent) savedProgress ?: 0f
                   else 0f

    val staticProgress = if (!isCurrent) savedProgress else null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = { if (hasSheetActions) showSheet = true },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            episode.podcastImage?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(episode.podcastName, relativeTime(episode.publishedAt), episode.duration)
                    .joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onPlay) {
                Icon(
                    imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                )
            }
        }
        when {
            isCurrent -> LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                drawStopIndicator = {},
            )
            staticProgress != null -> LinearProgressIndicator(
                progress = { staticProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                drawStopIndicator = {},
            )
        }
    }

    if (showSheet) {
        EpisodeActionsSheet(
            episodeTitle = episode.title,
            played = played,
            onAddToQueue = onAddToQueue,
            onTogglePlayed = onTogglePlayed?.let {
                {
                    val newPlayed = !played
                    played = newPlayed
                    it(newPlayed)
                }
            },
            onGoToPodcast = onGoToPodcast,
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EpisodeActionsSheet(
    episodeTitle: String,
    played: Boolean,
    onAddToQueue: (() -> Unit)?,
    onTogglePlayed: (() -> Unit)?,
    onGoToPodcast: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = episodeTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        if (onAddToQueue != null) {
            SheetAction(Icons.Default.PlaylistAdd, "Add to queue") {
                onAddToQueue(); onDismiss()
            }
        }
        if (onTogglePlayed != null) {
            SheetAction(
                Icons.Default.CheckCircle,
                if (played) "Mark as unplayed" else "Mark as played",
            ) { onTogglePlayed(); onDismiss() }
        }
        if (onGoToPodcast != null) {
            SheetAction(Icons.Default.Podcasts, "Go to podcast") {
                onGoToPodcast(); onDismiss()
            }
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
```

- [ ] **Step 2: Compile-check on the Mac**

Ask the user to run (on the Mac, from `android/`):

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`. If it fails on `combinedClickable`/`ModalBottomSheet` being
experimental, add `@file:OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)`
imports — but with BOM 2026.05.00 both are stable and no extra opt-in should be needed.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/components/EpisodeItem.kt
git commit -m "feat(android): declutter episode row to Play-only with long-press action sheet"
```

---

### Task 2: Wire "Go to podcast" on the Recent screen

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/screens/RecentScreen.kt`

- [ ] **Step 1: Add the navigation import**

Add this import alongside the existing `cast.android.ui.nav.EpisodeDetail` import:

```kotlin
import cast.android.ui.nav.PodcastDetail
```

- [ ] **Step 2: Pass `onGoToPodcast` to `EpisodeItem`**

In the `items(...)` block, change the `EpisodeItem(...)` call so it includes `onGoToPodcast`.
The full call becomes:

```kotlin
                    EpisodeItem(
                        episode = episode,
                        onPlay = { playerVm.playEpisode(episode) },
                        onTogglePlayed = { newPlayed -> vm.togglePlayed(episode.id, newPlayed) },
                        onAddToQueue = { vm.addToQueue(episode.id) },
                        onClick = { navController.navigate(EpisodeDetail(episode.id)) },
                        onGoToPodcast = episode.podcastId?.let { id ->
                            { navController.navigate(PodcastDetail(id)) }
                        },
                    )
```

Note: when `episode.podcastId` is null the lambda is null and the sheet omits "Go to podcast"
automatically. `PodcastDetailScreen` is intentionally left unchanged so its rows omit it too.

- [ ] **Step 3: Compile-check on the Mac**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/screens/RecentScreen.kt
git commit -m "feat(android): add Go to podcast action to Recent episode rows"
```

---

### Task 3: Verify on a device

**Files:** none (manual verification).

- [ ] **Step 1: Install on a connected device**

Ask the user to run (on the Mac, from `android/`):

Run: `./gradlew installDebug`
Expected: `BUILD SUCCESSFUL` and the app updates on the device.

- [ ] **Step 2: Walk the checklist on the device**

Confirm each, on both **Recent** and a **Podcast detail** screen:

- [ ] The row shows only one trailing button (Play/Pause). The ✓ and ☰+ icons are gone.
- [ ] Tapping a row opens the Episode detail screen (unchanged).
- [ ] Long-pressing a row opens the bottom sheet.
- [ ] Sheet shows **Add to queue** and **Mark as played/unplayed**; the mark label matches
      the episode's current state and flips after tapping it again.
- [ ] **Go to podcast** appears on **Recent** rows and navigates to that podcast; it is
      **absent** on Podcast detail rows.
- [ ] Tapping the Play button plays/pauses and does **not** open the sheet.
- [ ] The progress bar still renders under in-progress / current episodes.

- [ ] **Step 3: No commit** (verification only). If a defect is found, fix it in Task 1/2 and
      re-run this task.

---

### Task 4 (OPTIONAL): Instrumented UI test

Include this only if you want regression coverage. It requires a connected device/emulator
and `./gradlew connectedAndroidTest` (run on the Mac). It stubs `LocalPlayerViewModel`, so it
depends on that type's public surface — adjust the fake if construction differs.

**Files:**
- Create: `android/app/src/androidTest/kotlin/cast/android/ui/components/EpisodeItemTest.kt`

- [ ] **Step 1: Write the test**

```kotlin
package cast.android.ui.components

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.api.EpisodeDetailDto
import org.junit.Rule
import org.junit.Test

class EpisodeItemTest {
    @get:Rule val rule = createComposeRule()

    private fun episode() = EpisodeDetailDto(
        id = "e1",
        title = "Episode One",
        description = "",
        audioUrl = "http://x/a.mp3",
        duration = "10 min",
        durationMs = 600_000,
        publishedAt = null,
        played = false,
        progressMs = 0,
        podcastId = "p1",
        podcastName = "Pod",
        podcastImage = null,
    )

    @Test
    fun longPress_opensSheet_withActions() {
        rule.setContent {
            CompositionLocalProvider(LocalPlayerViewModel provides FakePlayerViewModel()) {
                EpisodeItem(
                    episode = episode(),
                    onPlay = {},
                    onTogglePlayed = {},
                    onAddToQueue = {},
                    onGoToPodcast = {},
                )
            }
        }

        rule.onNodeWithText("Episode One").performTouchInput { longClick() }

        rule.onNodeWithText("Add to queue").assertIsDisplayed()
        rule.onNodeWithText("Mark as played").assertIsDisplayed()
        rule.onNodeWithText("Go to podcast").assertIsDisplayed()
    }

    @Test
    fun playButton_invokesOnPlay() {
        var played = false
        rule.setContent {
            CompositionLocalProvider(LocalPlayerViewModel provides FakePlayerViewModel()) {
                EpisodeItem(episode = episode(), onPlay = { played = true })
            }
        }
        rule.onNodeWithContentDescription("Play").performClick()
        assert(played)
    }
}
```

- [ ] **Step 2: Provide `FakePlayerViewModel`**

`EpisodeItem` reads `currentMediaItem`, `isPlaying`, `position`, `duration`, and
`lastKnownProgress` from `LocalPlayerViewModel`. Create a fake exposing those as
`MutableStateFlow`s with default/empty values. Inspect
`android/app/src/main/kotlin/cast/android/ui/viewmodel/PlayerViewModel.kt` for the exact flow
types and either subclass it or extract an interface. (Left as a small adaptation step
because it depends on `PlayerViewModel`'s current constructor and visibility.)

- [ ] **Step 3: Run on the Mac**

Run: `./gradlew connectedAndroidTest`
Expected: both tests PASS.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/androidTest/kotlin/cast/android/ui/components/EpisodeItemTest.kt
git commit -m "test(android): cover episode row long-press sheet and play button"
```

---

## Notes for the implementer

- **`combinedClickable` import:** if the compiler reports it experimental, add
  `import androidx.compose.foundation.ExperimentalFoundationApi` and
  `@OptIn(ExperimentalFoundationApi::class)` on `EpisodeItem` and `SheetAction`.
- **`Icons.Default.Podcasts`** comes from `material-icons-extended`, already a dependency.
- **Don't touch** `QueueScreen` (separate `QueueEpisodeRow` with drag + swipe-to-delete),
  `EpisodeDetailScreen` (keeps its visible buttons — the discoverability backstop), or
  `CatalogScreen`.
- **Parked, not in this plan:** the "actively listening" podcast flag and episode multiselect.
