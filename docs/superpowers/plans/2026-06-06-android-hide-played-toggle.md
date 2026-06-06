# Android "Hide played episodes" toggle — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Android "Hide played episodes" toggle actually hide played episodes, by wiring the Android settings layer to the server's already-working filter.

**Architecture:** The server already filters played episodes in `GetPodcastDetail` based on a single global `hide_played` setting, exposed via `GET`/`PUT /api/settings`. The webapp already drives it. Android currently writes `hidePlayed` only to local DataStore and never calls the API. This plan makes `SettingsRepositoryImpl` push the toggle to the server on update and pull the server value on open. `hidePlayed` becomes server-owned (with a local DataStore cache for instant display); `serverUrl` stays local. No server changes, no PodcastDetail changes — `getPodcast` is uncached, so the next list load reflects the filter.

**Tech Stack:** Kotlin, Android (Hilt, Retrofit, Jetpack DataStore Preferences), JUnit4 + kotlinx-coroutines-test.

**Build note:** Android compiles/tests run on the build machine (Mac), not here. Where a step says "Run", execute it there with `cd android && ./gradlew test`.

---

## File Structure

- `android/app/src/main/kotlin/cast/android/domain/repository/SettingsRepository.kt` — add `refresh()` to the interface.
- `android/app/src/main/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImpl.kt` — inject `CastApiService`; push on `updateSettings`; implement `refresh()`.
- `android/app/src/main/kotlin/cast/android/ui/viewmodel/SettingsViewModel.kt` — call `refresh()` on init.
- `android/app/src/test/kotlin/cast/android/network/FakeCastApiService.kt` — make `getSettings`/`updateSettings` record/return values.
- `android/app/src/test/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImplTest.kt` — NEW: repo server-sync tests.
- `android/app/src/test/kotlin/cast/android/ui/viewmodel/FakeSettingsRepository.kt` — NEW: fake for the ViewModel test.
- `android/app/src/test/kotlin/cast/android/ui/viewmodel/SettingsViewModelTest.kt` — NEW: verifies refresh-on-init.

---

## Task 1: Wire SettingsRepository to the server

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/domain/repository/SettingsRepository.kt`
- Modify: `android/app/src/main/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImpl.kt`
- Modify: `android/app/src/test/kotlin/cast/android/network/FakeCastApiService.kt`
- Test: `android/app/src/test/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImplTest.kt` (new)

- [ ] **Step 1: Add `refresh()` to the interface and compile-only scaffolding**

In `SettingsRepository.kt`, add the method:

```kotlin
package cast.android.domain.repository

import cast.android.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun updateSettings(settings: Settings)
    suspend fun refresh()
}
```

In `SettingsRepositoryImpl.kt`, add the `CastApiService` constructor param and a **stub** `refresh()` (no behavior yet — so the failing test fails on assertions, not compilation). Leave `updateSettings` unchanged for now. Add the imports `cast.android.network.CastApiService` and `cast.api.SettingsDto` (SettingsDto used in Step 5).

```kotlin
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val baseUrlInterceptor: BaseUrlInterceptor,
    private val api: CastApiService,
) : SettingsRepository {
```

Add the stub method body (place it after `updateSettings`):

```kotlin
    override suspend fun refresh() {}
```

- [ ] **Step 2: Make `FakeCastApiService` record/return settings**

In `FakeCastApiService.kt`, add two fields to the constructor (after `queueAfterMutation`):

```kotlin
    /** Value returned by getSettings(); also what refresh() should pull. */
    var settings: SettingsDto = SettingsDto(hidePlayed = false),
    /** Captures the last SettingsDto passed to updateSettings(). */
    var updatedSettings: SettingsDto? = null,
```

Replace the two TODO lines:

```kotlin
    override suspend fun getSettings(): SettingsDto = TODO()
    override suspend fun updateSettings(settings: SettingsDto): Response<Unit> = TODO()
```

with:

```kotlin
    override suspend fun getSettings(): SettingsDto = settings
    override suspend fun updateSettings(settings: SettingsDto): Response<Unit> {
        updatedSettings = settings
        return Response.success(Unit)
    }
```

- [ ] **Step 3: Write the failing tests**

Create `android/app/src/test/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImplTest.kt`:

```kotlin
package cast.android.domain.repository.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import cast.android.domain.model.Settings
import cast.android.network.BaseUrlInterceptor
import cast.android.network.FakeCastApiService
import cast.api.SettingsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryImplTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope) {
            File(tmp.newFolder(), "settings.preferences_pb")
        }

    @Test
    fun `updateSettings pushes hidePlayed to the server`() = runTest {
        val api = FakeCastApiService()
        val repo = SettingsRepositoryImpl(dataStore(backgroundScope), BaseUrlInterceptor(), api)

        repo.updateSettings(Settings(serverUrl = "http://host", hidePlayed = true))

        assertEquals(SettingsDto(hidePlayed = true), api.updatedSettings)
    }

    @Test
    fun `refresh writes the server hidePlayed into settings`() = runTest {
        val api = FakeCastApiService(settings = SettingsDto(hidePlayed = true))
        val repo = SettingsRepositoryImpl(dataStore(backgroundScope), BaseUrlInterceptor(), api)

        repo.refresh()

        assertEquals(true, repo.settings.first().hidePlayed)
    }
}
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `cd android && ./gradlew test --tests "cast.android.domain.repository.impl.SettingsRepositoryImplTest"`
Expected: FAIL — `updatedSettings` is `null` (push not implemented) and `hidePlayed` is `false` (refresh is a no-op stub).

- [ ] **Step 5: Implement the repository behavior**

In `SettingsRepositoryImpl.kt`, add `import cast.android.network.orThrow`. Change `updateSettings` to push to the server after the local write (set `baseUrl` before the call so the PUT targets the right host), and implement `refresh`:

```kotlin
    override suspend fun updateSettings(settings: Settings) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = settings.serverUrl
            prefs[HIDE_PLAYED] = settings.hidePlayed
        }
        baseUrlInterceptor.baseUrl = settings.serverUrl
        api.updateSettings(SettingsDto(hidePlayed = settings.hidePlayed)).orThrow()
    }

    override suspend fun refresh() {
        val remote = api.getSettings()
        dataStore.edit { prefs -> prefs[HIDE_PLAYED] = remote.hidePlayed }
    }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `cd android && ./gradlew test --tests "cast.android.domain.repository.impl.SettingsRepositoryImplTest"`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/domain/repository/SettingsRepository.kt \
        android/app/src/main/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImpl.kt \
        android/app/src/test/kotlin/cast/android/network/FakeCastApiService.kt \
        android/app/src/test/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImplTest.kt
git commit -m "feat(android): push hide-played setting to server and refresh from it"
```

---

## Task 2: Refresh server settings when the Settings screen opens

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/viewmodel/SettingsViewModel.kt`
- Test: `android/app/src/test/kotlin/cast/android/ui/viewmodel/FakeSettingsRepository.kt` (new)
- Test: `android/app/src/test/kotlin/cast/android/ui/viewmodel/SettingsViewModelTest.kt` (new)

- [ ] **Step 1: Add a fake SettingsRepository for the ViewModel test**

Create `android/app/src/test/kotlin/cast/android/ui/viewmodel/FakeSettingsRepository.kt`:

```kotlin
package cast.android.ui.viewmodel

import cast.android.domain.model.Settings
import cast.android.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(Settings())
    var refreshCount = 0
    override suspend fun updateSettings(settings: Settings) { this.settings.value = settings }
    override suspend fun refresh() { refreshCount++ }
}
```

- [ ] **Step 2: Write the failing test**

Create `android/app/src/test/kotlin/cast/android/ui/viewmodel/SettingsViewModelTest.kt`:

```kotlin
package cast.android.ui.viewmodel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SettingsViewModelTest {

    @Before fun setUp() { Dispatchers.setMain(StandardTestDispatcher()) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test
    fun `refreshes server settings on init`() = runTest {
        val repo = FakeSettingsRepository()

        SettingsViewModel(repo)
        advanceUntilIdle()

        assertEquals(1, repo.refreshCount)
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd android && ./gradlew test --tests "cast.android.ui.viewmodel.SettingsViewModelTest"`
Expected: FAIL — `refreshCount` is `0` (the ViewModel does not refresh yet).

- [ ] **Step 4: Implement refresh-on-init**

In `SettingsViewModel.kt`, add an `init` block (after the `settings` property). The `runCatching` keeps a refresh failure (e.g. offline) from crashing `viewModelScope`; the local DataStore cache still shows the last-known toggle state.

```kotlin
    init {
        viewModelScope.launch { runCatching { settingsRepository.refresh() } }
    }
```

`viewModelScope` and `launch` are already imported in this file.

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd android && ./gradlew test --tests "cast.android.ui.viewmodel.SettingsViewModelTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/viewmodel/SettingsViewModel.kt \
        android/app/src/test/kotlin/cast/android/ui/viewmodel/FakeSettingsRepository.kt \
        android/app/src/test/kotlin/cast/android/ui/viewmodel/SettingsViewModelTest.kt
git commit -m "feat(android): refresh hide-played setting from server when settings open"
```

---

## Task 3: Full verification

- [ ] **Step 1: Run the whole Android unit test suite**

Run: `cd android && ./gradlew test`
Expected: BUILD SUCCESSFUL, all tests pass (including the existing `RecentViewModelTest`, `EpisodeRepositoryImplTest`, etc.).

- [ ] **Step 2: Manual smoke test on a device/emulator**

Run: `cd android && ./gradlew installDebug` (server must be running and reachable at the configured `serverUrl`).
1. Subscribe to / open a podcast that has at least one played episode.
2. Open Settings, turn **Hide played episodes** ON.
3. Reopen the podcast detail — the played episode(s) should be gone.
4. Open the webapp Settings — the toggle should also read ON (shared global setting).
5. Turn it OFF in either client; the played episodes reappear on the next podcast-detail load.

Expected behaviors:
- Played episodes disappear from the podcast detail list when the toggle is ON.
- The toggle reflects whatever the webapp last set when you open Android Settings (refresh-on-init).

**Known limitation (by design, do not fix here):** if you mark an episode played *while viewing a podcast detail* with the toggle ON, it stays visible (shown as played) until the next load — the optimistic in-place update in `PodcastDetailViewModel.togglePlayed` doesn't know about the server setting.

---

## Self-Review Notes

- **Spec coverage:** push-on-update (Task 1, Step 5), refresh-from-server (Task 1, Step 5 + Task 2), interface `refresh()` (Task 1, Step 1), FakeCastApiService stubs (Task 1, Step 2), no UI/PodcastDetail changes (verified: server-side filter + uncached `getPodcast`), error behavior (Task 2, Step 4 `runCatching` + eventual reconcile via refresh), known limitation (Task 3, Step 2). All covered.
- **No server changes:** `GetPodcastDetail`, `SettingsApi`, `SQLiteSettingsPersistence` are already complete and untouched.
- **Type consistency:** `refresh()` signature, `SettingsDto(hidePlayed = ...)`, and `Settings(serverUrl, hidePlayed)` match the existing models throughout.
