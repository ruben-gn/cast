# Android Button-Press Visual Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give action buttons (Add to queue, Save server URL, Mark all played) an immediate, visual-only confirmation on tap so the press clearly registers.

**Architecture:** Two small reusable composables in `ui/components/` plus one shared timer helper. Confirmation is pure UI-local state that fires on tap (no ViewModel changes, no backend result plumbing). An icon button morphs to a checkmark for ~1.2s; a text button swaps its label (e.g. "Saved ✓") for ~1.2s.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), `Crossfade` animation, `LaunchedEffect` + `kotlinx.coroutines.delay`.

---

## Build / verification note

Builds and instrumented tests run on a **separate machine** — do NOT run `./gradlew` in this session. Where a step says "build", that is an action for the user to run on their build machine:

```bash
cd android && ./gradlew assembleDebug
```

The helpers are trivial Compose UI with no testable business logic, so this plan relies on a compile check plus a manual eyeball pass (Task 5), not automated UI tests.

---

## File structure

| File | Responsibility | Action |
|------|----------------|--------|
| `android/app/src/main/kotlin/cast/android/ui/components/ConfirmFeedback.kt` | `rememberConfirmTrigger`, `ConfirmIconButton`, `ConfirmButton` | Create |
| `android/app/src/main/kotlin/cast/android/ui/components/EpisodeItem.kt` | Add-to-queue row button | Modify |
| `android/app/src/main/kotlin/cast/android/ui/screens/EpisodeDetailScreen.kt` | Add-to-queue detail button | Modify |
| `android/app/src/main/kotlin/cast/android/ui/screens/SettingsScreen.kt` | Save-server button | Modify |
| `android/app/src/main/kotlin/cast/android/ui/screens/PodcastDetailScreen.kt` | Mark-all-played button | Modify |

---

### Task 1: Create the reusable confirm-feedback components

**Files:**
- Create: `android/app/src/main/kotlin/cast/android/ui/components/ConfirmFeedback.kt`

- [ ] **Step 1: Create the file with all three components**

```kotlin
package cast.android.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.delay

/**
 * Returns a [confirmed] flag plus a [trigger]. Calling trigger() flips confirmed
 * to true; it auto-resets to false after [durationMs]. Visual-only, fire-on-tap.
 */
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

/**
 * IconButton that briefly morphs [icon] into a primary-tinted checkmark on tap,
 * confirming the action registered. Runs [onClick] immediately (fire-on-tap).
 */
@Composable
fun ConfirmIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val (confirmed, trigger) = rememberConfirmTrigger()
    IconButton(
        onClick = {
            onClick()
            trigger()
        },
        modifier = modifier,
    ) {
        Crossfade(targetState = confirmed, label = "confirmIcon") { isConfirmed ->
            if (isConfirmed) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = tint,
                )
            }
        }
    }
}

/**
 * Material3 Button that briefly swaps its [text] label for [confirmedText] on tap.
 * Runs [onClick] immediately (fire-on-tap).
 */
@Composable
fun ConfirmButton(
    text: String,
    confirmedText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val (confirmed, trigger) = rememberConfirmTrigger()
    Button(
        onClick = {
            onClick()
            trigger()
        },
        enabled = enabled,
        modifier = modifier,
    ) {
        Text(if (confirmed) confirmedText else text)
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/components/ConfirmFeedback.kt
git commit -m "feat(android): add reusable confirm-feedback components"
```

---

### Task 2: Wire Add-to-queue feedback (shared row + episode detail)

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/components/EpisodeItem.kt:132-140`
- Modify: `android/app/src/main/kotlin/cast/android/ui/screens/EpisodeDetailScreen.kt:168-170`

- [ ] **Step 1: Replace the add-to-queue IconButton in `EpisodeItem.kt`**

Find this block (currently lines 132-140):

```kotlin
                    if (onAddToQueue != null) {
                        IconButton(onClick = onAddToQueue) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "Add to queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
```

Replace with:

```kotlin
                    if (onAddToQueue != null) {
                        ConfirmIconButton(
                            icon = Icons.Default.PlaylistAdd,
                            contentDescription = "Add to queue",
                            onClick = onAddToQueue,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
```

- [ ] **Step 2: Remove the now-unused `IconButton` import in `EpisodeItem.kt` only if no other usage remains**

`EpisodeItem.kt` still uses `IconButton` for the play and toggle-played buttons (lines 119, 141), so KEEP the `import androidx.compose.material3.IconButton`. No import change needed. `ConfirmIconButton` is in the same package (`cast.android.ui.components`), so no new import is required.

- [ ] **Step 3: Replace the add-to-queue IconButton in `EpisodeDetailScreen.kt`**

Find this block (currently lines 168-170):

```kotlin
            IconButton(onClick = onAddToQueue) {
                Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to queue")
            }
```

Replace with:

```kotlin
            ConfirmIconButton(
                icon = Icons.Default.PlaylistAdd,
                contentDescription = "Add to queue",
                onClick = onAddToQueue,
            )
```

- [ ] **Step 4: Add the import in `EpisodeDetailScreen.kt`**

Add this import alphabetically among the `cast.android.ui.*` imports (near line 51-53, e.g. before `import cast.android.ui.UiState`):

```kotlin
import cast.android.ui.components.ConfirmIconButton
```

`EpisodeDetailScreen.kt` still uses `IconButton` for the back nav and mark-played buttons (lines 72, 171), so keep its `IconButton` import.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/components/EpisodeItem.kt \
        android/app/src/main/kotlin/cast/android/ui/screens/EpisodeDetailScreen.kt
git commit -m "feat(android): checkmark confirmation on add-to-queue"
```

---

### Task 3: Wire Save-server-URL feedback

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/screens/SettingsScreen.kt:63-69`

- [ ] **Step 1: Replace the Save button**

Find this block (currently lines 63-69):

```kotlin
        Button(
            onClick = { vm.updateSettings(settings.copy(serverUrl = serverUrl)) },
            enabled = serverUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Save server URL")
        }
```

Replace with:

```kotlin
        ConfirmButton(
            text = "Save server URL",
            confirmedText = "Saved ✓",
            onClick = { vm.updateSettings(settings.copy(serverUrl = serverUrl)) },
            enabled = serverUrl.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        )
```

- [ ] **Step 2: Add the import and remove the now-unused `Button` import**

Add (alphabetically near the other `cast.android` import at line 30):

```kotlin
import cast.android.ui.components.ConfirmButton
```

The `Button` composable is no longer used in `SettingsScreen.kt` (it had only this one Button). Remove the now-unused import at line 12:

```kotlin
import androidx.compose.material3.Button
```

`Text` is still used elsewhere in the file (e.g. the "Settings" title, label, switch row), so keep its import.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/screens/SettingsScreen.kt
git commit -m "feat(android): \"Saved ✓\" confirmation on save server URL"
```

---

### Task 4: Wire Mark-all-played feedback

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/screens/PodcastDetailScreen.kt:115-117`

- [ ] **Step 1: Replace the Mark-all-played button**

Find this block (currently lines 115-117):

```kotlin
                                Button(onClick = { vm.markAllPlayed() }) {
                                    Text("Mark all played")
                                }
```

Replace with:

```kotlin
                                ConfirmButton(
                                    text = "Mark all played",
                                    confirmedText = "Done ✓",
                                    onClick = { vm.markAllPlayed() },
                                )
```

- [ ] **Step 2: Add the import; check the `Button` import**

Add the `ConfirmButton` import alphabetically among the existing `cast.android.ui.*` imports:

```kotlin
import cast.android.ui.components.ConfirmButton
```

Then check the rest of `PodcastDetailScreen.kt` for any other `Button(` usage. If `Button` is no longer used anywhere in the file, remove `import androidx.compose.material3.Button`. If it is still used elsewhere, keep the import.

Run: `grep -n "Button(" android/app/src/main/kotlin/cast/android/ui/screens/PodcastDetailScreen.kt`
Expected after edit: only `ConfirmButton(` should remain (no bare `Button(`). If so, remove the `Button` import; otherwise keep it.

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/screens/PodcastDetailScreen.kt
git commit -m "feat(android): \"Done ✓\" confirmation on mark all played"
```

---

### Task 5: Build and manual verification (on build machine)

**Files:** none (verification only)

- [ ] **Step 1: Compile on the build machine**

Run: `cd android && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL with no unused-import or unresolved-reference warnings for the touched files.

- [ ] **Step 2: Install and eyeball each button**

Run: `cd android && ./gradlew installDebug`

Verify on device:
1. **Recent screen** — tap a row's add-to-queue (playlist-add) icon → it flips to a primary-tinted checkmark, then reverts after ~1.2s.
2. **Podcast detail** — same add-to-queue behavior on episode rows.
3. **Episode detail** — tap the add-to-queue icon → checkmark, then reverts.
4. **Settings** — tap "Save server URL" → label shows "Saved ✓" briefly, then reverts.
5. **Podcast detail** — tap "Mark all played" → label shows "Done ✓" briefly, then reverts.
6. Tap any of them twice quickly → confirmation re-triggers cleanly (timer resets, no flicker/stuck state).

- [ ] **Step 3: No commit** (verification only)

---

## Self-review

**Spec coverage:**
- Add to queue (EpisodeItem + EpisodeDetailScreen) → Task 2 ✓
- Save server URL → Task 3 ✓
- Mark all played → Task 4 ✓
- Subscribe left as-is → not in plan, correct per spec ✓
- Fire-on-tap, ViewModels untouched → no ViewModel edits in any task ✓
- Visual-only, ~1.2s, Crossfade morph, "Saved ✓"/"Done ✓" labels → Task 1 + call sites ✓
- Offline-checkmark-can-lie limitation → accepted, no code change needed ✓

**Placeholder scan:** none — all steps show concrete code, exact paths, exact commands.

**Type consistency:** `rememberConfirmTrigger(): Pair<Boolean, () -> Unit>`, `ConfirmIconButton(icon, contentDescription, onClick, modifier, tint)`, `ConfirmButton(text, confirmedText, onClick, modifier, enabled)` — signatures used at every call site match Task 1's definitions.
