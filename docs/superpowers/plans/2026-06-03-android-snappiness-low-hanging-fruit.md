# Implementation Plan: Android Snappiness — Low-Hanging Fruit

**Spec:** [`docs/superpowers/specs/2026-06-02-android-snappiness-design.md`](../specs/2026-06-02-android-snappiness-design.md)
**Date:** 2026-06-03
**Scope:** Client-only. No Room, no backend changes, no WebSocket-protocol changes.

## Context for the implementer

This plan delivers the three snappiness wins that need **zero backend work** and **zero new
persistence layer**. They are the cheapest slices of the larger design spec; the spec's bigger
items (Room read-through cache, shared download cache, backend domain-decision endpoints, offline
outbox, server last-writer-wins fix) are explicitly **out of scope here** and stay deferred.

Three independent features, each shippable on its own:

- **Feature A — Stale-while-revalidate list screens.** Recent / Podcasts / Queue currently flash a
  spinner every time you open them because each `ViewModel.init` sets `UiState.Loading` then live-
  fetches. Add a tiny in-memory cache in the three `@Singleton` repositories and seed the ViewModel
  from it, so a revisit paints last-known data instantly and refreshes in the background.
- **Feature B — ExoPlayer disk cache.** Audio is re-streamed cold every play. Add a Media3
  `SimpleCache` + `CacheDataSource` so already-heard bytes (and look-back/replays) come off disk, and
  tag each item with `customCacheKey = episode.id` so the cache key is the stable episode id rather
  than the (potentially rotating) audio URL.
- **Feature C — Seek-to-local-progress.** On `onMediaItemTransition` we currently wait for the
  WebSocket `get` round-trip before seeking to the resume position, so playback visibly jumps. Cache
  per-episode progress in DataStore at the points we already sync, and seek to it **immediately** on
  transition — without marking the episode "started", so the existing server reconcile still re-seeks
  to the authoritative value when it arrives.

### Source-of-truth invariant (do not violate)

The Pi server is the **absolute source of truth**. Everything added here is a **cache/optimization**
that the server always overwrites:

- Feature A's repo cache is last-known data shown *while* a live fetch runs; the fetch result
  replaces it. Never treat the cache as authoritative.
- Feature C's DataStore progress is a *head-start* seek only. The WebSocket `get` reconcile
  (`states.collect` in `PlaybackService`) MUST still run and re-seek to the server value. Do **not**
  set `episodeStarted = true` from the local seek, or you suppress the reconcile.

### Build & test constraint

**Never run `./gradlew` yourself.** Android builds run on a separate machine. Every build/test
command in this plan is for **the user** to run (they can prefix with `! ` to run in-session). After
each code task, state the command the user should run and wait for their confirmation before moving
on.

### TDD posture

- **Pure logic** (the cache holder, the progress-seek decision) → real unit tests, written first.
- **Android-framework wiring** (Hilt module building a `SimpleCache`, `MediaItem` with a `Uri`,
  ExoPlayer factory) → verified by build + manual device check, because unit-testing it needs
  Robolectric/instrumentation scaffolding disproportionate to the payoff. These tasks say so
  explicitly.

---

## Feature A — Stale-while-revalidate list screens

> **Test scaffolding note (read before A2).** The Android module has **no test infrastructure** —
> only `android/app/src/test/kotlin/cast/android/ExampleUnitTest.kt` exists. There is no fake
> `CastApiService`, no test helpers. So **A0 below writes them from scratch**, once, and A2/A3 reuse
> them. Approach chosen: a **hand-written fake** implementing the `CastApiService` Retrofit interface
> (cleaner than MockWebServer here — the repos are thin pass-throughs, so we want to assert on the
> object returned, not parse JSON). Test framework is **JUnit4 + backtick function names** (`libs.junit`
> is the Android test dep; Kotest is the `core/` backend convention only — do **not** pull it into
> Android).

### A0. Test scaffolding: `FakeCastApiService` + DTO builder (one-time)

**New file:** `android/app/src/test/kotlin/cast/android/network/FakeCastApiService.kt`

A hand-written fake of the full `CastApiService` interface. Unused endpoints throw
`NotImplementedError`; the list endpoints used by the repo tests are backed by mutable fields so a
test can script "first fetch returns X, after remove returns Y".

```kotlin
package cast.android.network

import cast.api.AddPodcastRequest
import cast.api.EpisodeDetailDto
import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import cast.api.ReorderQueueRequest
import cast.api.SettingsDto
import okhttp3.MultipartBody
import retrofit2.Response

/** Builds an [EpisodeDetailDto] with only an id varying; everything else gets harmless defaults. */
fun episode(id: String, played: Boolean = false, progressMs: Long = 0L) = EpisodeDetailDto(
    id = id,
    title = "Episode $id",
    description = "",
    audioUrl = "https://example.test/$id.mp3",
    duration = null,
    durationMs = null,
    publishedAt = null,
    played = played,
    progressMs = progressMs,
)

class FakeCastApiService(
    var recent: List<EpisodeDetailDto> = emptyList(),
    var queue: List<EpisodeDetailDto> = emptyList(),
    var podcasts: List<PodcastSummaryDto> = emptyList(),
    /** Queue contents the mutation endpoints return; defaults to [queue] if unset. */
    var queueAfterMutation: List<EpisodeDetailDto>? = null,
) : CastApiService {

    private fun mutatedQueue() = queueAfterMutation ?: queue

    override suspend fun getRecentEpisodes(): List<EpisodeDetailDto> = recent
    override suspend fun getQueue(): List<EpisodeDetailDto> = queue
    override suspend fun listPodcasts(): List<PodcastSummaryDto> = podcasts

    override suspend fun addToQueue(episodeId: String): List<EpisodeDetailDto> = mutatedQueue()
    override suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto> = mutatedQueue()
    override suspend fun reorderQueue(request: ReorderQueueRequest): List<EpisodeDetailDto> = mutatedQueue()

    // Unused by the low-hanging-fruit tests.
    override suspend fun getPodcast(id: String): PodcastDetailDto = TODO()
    override suspend fun addPodcast(request: AddPodcastRequest): PodcastDetailDto = TODO()
    override suspend fun markAllPodcastPlayed(id: String): Response<Unit> = TODO()
    override suspend fun getEpisode(episodeId: String): EpisodeDetailDto = TODO()
    override suspend fun markPlayed(episodeId: String): Response<Unit> = TODO()
    override suspend fun markUnplayed(episodeId: String): Response<Unit> = TODO()
    override suspend fun importOpml(file: MultipartBody.Part): Response<Unit> = TODO()
    override suspend fun getSettings(): SettingsDto = TODO()
    override suspend fun updateSettings(settings: SettingsDto): Response<Unit> = TODO()
}
```

> `coroutines-test` (`runTest`) is already a test dependency. No new Gradle entries needed.
> **Delete** `ExampleUnitTest.kt` once a real test exists, or leave it — harmless either way.

**Commit:** `android: add FakeCastApiService + DTO builder test scaffolding`

### A1. Add a generic in-memory `LatestCache<T>` (TDD)

**New file:** `android/app/src/main/kotlin/cast/android/domain/cache/LatestCache.kt`

A trivially thread-safe holder for "the last value we successfully fetched". One instance per cached
repository method.

```kotlin
package cast.android.domain.cache

import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the most recent successfully-fetched value for stale-while-revalidate reads.
 * The server remains the source of truth: [latest] is only ever shown while a fresh fetch runs,
 * and [put] overwrites it with the server's response.
 */
class LatestCache<T : Any> {
    private val ref = AtomicReference<T?>(null)

    val latest: T? get() = ref.get()

    fun put(value: T) { ref.set(value) }

    fun clear() { ref.set(null) }
}
```

**Test first** — `android/app/src/test/kotlin/cast/android/domain/cache/LatestCacheTest.kt`:

```kotlin
package cast.android.domain.cache

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LatestCacheTest {

    @Test
    fun `latest is null before anything is cached`() {
        val cache = LatestCache<List<String>>()
        assertNull(cache.latest)
    }

    @Test
    fun `latest returns the most recently put value`() {
        val cache = LatestCache<List<String>>()
        cache.put(listOf("a"))
        cache.put(listOf("a", "b"))
        assertEquals(listOf("a", "b"), cache.latest)
    }

    @Test
    fun `clear resets latest to null`() {
        val cache = LatestCache<List<String>>()
        cache.put(listOf("a"))
        cache.clear()
        assertNull(cache.latest)
    }
}
```

**User runs:** `cd android && ./gradlew :app:testDebugUnitTest --tests "cast.android.domain.cache.LatestCacheTest"`

**Commit:** `android: add LatestCache holder for stale-while-revalidate`

---

### A2. Cache reads in the three list repositories (TDD)

Wire a `LatestCache` into each `@Singleton` repository's list method: on success, `put` the result;
expose a synchronous `cachedX()` for the ViewModel to seed from. The fetch itself is unchanged — the
server response always wins.

**`EpisodeRepository` interface** — add:
```kotlin
fun cachedRecentEpisodes(): List<EpisodeDetailDto>?
```
**`EpisodeRepositoryImpl`:**
```kotlin
@Singleton
class EpisodeRepositoryImpl @Inject constructor(
    private val api: CastApiService,
) : EpisodeRepository {

    private val recentCache = LatestCache<List<EpisodeDetailDto>>()

    override fun cachedRecentEpisodes(): List<EpisodeDetailDto>? = recentCache.latest

    override suspend fun getRecentEpisodes(): List<EpisodeDetailDto> =
        api.getRecentEpisodes().also(recentCache::put)

    // Toggling played changes which episodes belong in "recent". Marking it stale (clear, don't
    // patch) means the next revisit cold-loads that one screen rather than seeding a list that
    // briefly flashes the just-played episode back before the background refresh removes it.
    override suspend fun markPlayed(episodeId: String) {
        api.markPlayed(episodeId)
        recentCache.clear()
    }

    override suspend fun markUnplayed(episodeId: String) {
        api.markUnplayed(episodeId)
        recentCache.clear()
    }

    // getEpisode unchanged
}
```
(Add `import cast.android.domain.cache.LatestCache`.)

> This requires a `clear()` on `LatestCache` — add it in A1:
> ```kotlin
> fun clear() { ref.set(null) }
> ```

**`PodcastRepository` interface** — add:
```kotlin
fun cachedPodcasts(): List<PodcastSummaryDto>?
```
**`PodcastRepositoryImpl`:**
```kotlin
private val podcastsCache = LatestCache<List<PodcastSummaryDto>>()

override fun cachedPodcasts(): List<PodcastSummaryDto>? = podcastsCache.latest

override suspend fun listPodcasts(): List<PodcastSummaryDto> =
    api.listPodcasts().also(podcastsCache::put)
```

**`QueueRepository` interface** — add:
```kotlin
fun cachedQueue(): List<EpisodeDetailDto>?
```
**`QueueRepositoryImpl`:**
```kotlin
private val queueCache = LatestCache<List<EpisodeDetailDto>>()

override fun cachedQueue(): List<EpisodeDetailDto>? = queueCache.latest

override suspend fun getQueue(): List<EpisodeDetailDto> =
    api.getQueue().also(queueCache::put)
```

> **Keep the cache warm on mutations.** `QueueRepositoryImpl.addToQueue` / `removeFromQueue` /
> `reorderQueue` all return the new authoritative list from the server — feed those into the cache
> too so the next screen-open seeds the post-mutation state, not a stale pre-mutation one:
> ```kotlin
> override suspend fun removeFromQueue(episodeId: String): List<EpisodeDetailDto> =
>     api.removeFromQueue(episodeId).also(queueCache::put)
> ```
> Apply the same `.also(queueCache::put)` to `addToQueue` and `reorderQueue`.

**Test first** — `android/app/src/test/kotlin/cast/android/domain/repository/impl/QueueRepositoryImplTest.kt`,
using the `FakeCastApiService` + `episode()` from A0:

```kotlin
package cast.android.domain.repository.impl

import cast.android.network.FakeCastApiService
import cast.android.network.episode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRepositoryImplTest {

    @Test
    fun `cachedQueue is null before any fetch`() = runTest {
        val repo = QueueRepositoryImpl(FakeCastApiService(queue = listOf(episode("1"))))
        assertNull(repo.cachedQueue())
    }

    @Test
    fun `cachedQueue returns the last fetched queue`() = runTest {
        val repo = QueueRepositoryImpl(FakeCastApiService(queue = listOf(episode("1"), episode("2"))))
        repo.getQueue()
        assertEquals(listOf("1", "2"), repo.cachedQueue()?.map { it.id })
    }

    @Test
    fun `removeFromQueue updates the cache`() = runTest {
        val repo = QueueRepositoryImpl(
            FakeCastApiService(
                queue = listOf(episode("1"), episode("2")),
                queueAfterMutation = listOf(episode("2")),
            )
        )
        repo.getQueue()
        repo.removeFromQueue("1")
        assertEquals(listOf("2"), repo.cachedQueue()?.map { it.id })
    }
}
```

Add an equivalent `EpisodeRepositoryImplTest` (cachedRecentEpisodes null → populated → cleared by
`markPlayed`) and `PodcastRepositoryImplTest` (cachedPodcasts null → populated). For the `markPlayed`
clear test, give the fake a non-throwing `markPlayed` by subclassing or extend `FakeCastApiService`
to return `Response.success(Unit)` from `markPlayed`/`markUnplayed` (swap the `TODO()` for
`Response.success(Unit)` on those two methods in A0 if you write that test).

**User runs:** `cd android && ./gradlew :app:testDebugUnitTest --tests "cast.android.domain.repository.impl.*"`

**Commit:** `android: cache list reads in repositories for stale-while-revalidate`

---

### A3. Seed ViewModels from cache (no spinner on revisit)

In each list ViewModel, seed `_uiState` from the cache so a revisit starts at `Success(cached)`
instead of `Loading`. Only show `Loading` on a true cold start (no cached data). The background
refresh runs regardless and overwrites with the server's response.

**`RecentViewModel`:**
```kotlin
private val _uiState = MutableStateFlow<UiState<List<EpisodeDetailDto>>>(
    episodeRepository.cachedRecentEpisodes()?.let { UiState.Success(it) } ?: UiState.Loading
)
val uiState: StateFlow<UiState<List<EpisodeDetailDto>>> = _uiState.asStateFlow()

init { load() }

fun load() {
    viewModelScope.launch {
        if (_uiState.value !is UiState.Success) _uiState.value = UiState.Loading
        _uiState.value = try {
            UiState.Success(episodeRepository.getRecentEpisodes())
        } catch (e: Exception) {
            // Keep showing cached data on refresh failure; only surface Error on cold start.
            if (_uiState.value is UiState.Success) _uiState.value
            else UiState.Error(e.message ?: "Failed to load episodes")
        }
    }
}
```

Apply the same shape to:
- **`CatalogViewModel`** — seed from `podcastRepository.cachedPodcasts()`, message `"Failed to load podcasts"`.
- **`QueueViewModel`** — seed from `queueRepository.cachedQueue()`, message `"Failed to load queue"`.

> **Why "keep cached on refresh failure":** going offline shouldn't blank a screen that was showing
> good data a second ago. A failed *refresh* leaves the stale-but-useful list; only a *cold* start
> with no cache shows the error. This matches the spec's local-first intent without adding Room.

**ViewModel seeding is unit-testable** with hand-written fake repositories (the repository types are
interfaces). The one behaviour worth asserting is "warm cache → no `Loading`": construct the VM with
a fake whose `cachedRecentEpisodes()` returns data and check `uiState.value` is `Success` *before*
the coroutine refresh runs. Because `init { load() }` launches on `viewModelScope` (Main dispatcher),
in a plain JUnit test the launched refresh won't run until you pump a dispatcher — so the
*synchronously-seeded* initial value is exactly what `uiState.value` holds:

```kotlin
@Test
fun `seeds from cache as Success without Loading flash`() {
    val episodes = listOf(episode("1"))
    val vm = RecentViewModel(
        episodeRepository = FakeEpisodeRepository(cachedRecent = episodes),
        queueRepository = FakeQueueRepository(),
    )
    assertEquals(UiState.Success(episodes), vm.uiState.value)
}
```

Write minimal `FakeEpisodeRepository` / `FakeQueueRepository` implementing the interfaces (cached
getters return the constructor arg; suspend fetchers return the same or empty; mutations no-op). If
the launched `load()` interferes (it can, if `getRecentEpisodes()` resolves synchronously), inject a
fake whose suspend `getRecentEpisodes()` suspends forever or throws — the test only cares about the
seeded value. Keep this fake in `src/test`; do **not** over-engineer a dispatcher rule here.

**User runs:** `cd android && ./gradlew :app:testDebugUnitTest` then `cd android && ./gradlew :app:assembleDebug`

**Manual device check:** open Recent → Podcasts → Queue, navigate away and back. Second visit paints
instantly with no spinner; data still refreshes (pull-to-refresh / re-fetch updates it).

**Commit:** `android: seed list ViewModels from cache to skip loading flash`

---

## Feature B — ExoPlayer disk cache

> **Dependency note:** `SimpleCache`, `CacheDataSource`, `LeastRecentlyUsedCacheEvictor` live in
> `androidx.media3:media3-datasource`; `StandaloneDatabaseProvider` lives in
> `androidx.media3:media3-database`. Both are transitive via `media3-exoplayer` (already a
> dependency), so no new Gradle deps should be needed. If the build can't resolve them, add explicit
> entries to `libs.versions.toml` (`media3-datasource`, `media3-database`, same `media3` version
> ref) and `app/build.gradle.kts`.

### B1. Provide a singleton `SimpleCache` + `CacheDataSource.Factory` (wiring; build-verified)

**New file:** `android/app/src/main/kotlin/cast/android/di/PlaybackCacheModule.kt`

```kotlin
package cast.android.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object PlaybackCacheModule {

    // Single LRU cache for streamed audio. Downloads will later want a separate non-evicting
    // cache region (see spec §3, deferred); for now one cache serves all playback.
    private const val MAX_CACHE_BYTES = 512L * 1024 * 1024 // 512 MB

    @Provides
    @Singleton
    fun provideSimpleCache(@ApplicationContext context: Context): SimpleCache =
        SimpleCache(
            File(context.cacheDir, "media"),
            LeastRecentlyUsedCacheEvictor(MAX_CACHE_BYTES),
            StandaloneDatabaseProvider(context),
        )

    @Provides
    @Singleton
    fun provideCacheDataSourceFactory(
        @ApplicationContext context: Context,
        cache: SimpleCache,
    ): CacheDataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
```

> `SimpleCache` must be a process-wide singleton — instantiating two over the same directory throws.
> Hilt `@Singleton` guarantees that. `FLAG_IGNORE_CACHE_ON_ERROR` keeps playback working if the cache
> read fails (falls back to network).

**Not unit-tested** (builds an Android-framework cache against the filesystem). Verified by the build
in B2.

**Commit:** `android: provide Media3 SimpleCache + CacheDataSource factory`

---

### B2. Wire the cache into ExoPlayer and tag items with `customCacheKey` (wiring; manual-verified)

**`PlaybackService`** — inject the factory and pass it to ExoPlayer via a
`DefaultMediaSourceFactory`, and set the stable cache key on each playable item.

Add the import and field:
```kotlin
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
// ...
@Inject lateinit var cacheDataSourceFactory: CacheDataSource.Factory
```

In `onCreate`, build the player with the caching media-source factory:
```kotlin
val player = ExoPlayer.Builder(this)
    .setAudioAttributes(audioAttributes, true)
    .setHandleAudioBecomingNoisy(true)
    .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
    .build()
    .also { it.addListener(PlayerListener()) }
```

In `playableItem`, set the cache key to the episode id so caching survives audio-URL changes:
```kotlin
return MediaItem.Builder()
    .setMediaId(episode.id)
    .setUri(episode.audioUrl)
    .setCustomCacheKey(episode.id)
    .setMediaMetadata(metadata)
    .build()
```

> **Why `customCacheKey = episode.id`:** the default cache key is the URL. Podcast audio URLs can
> carry rotating tracking/signing query params, which would defeat the cache (every play = cache
> miss). Pinning the key to the stable episode id makes the cache hit reliably. The id is already the
> `mediaId`, so there's no new lookup. This also aligns the streaming cache key with the future
> download cache key (spec §3) so downloaded + streamed bytes share one entry later.

**Not unit-tested** (ExoPlayer + framework). Verify by build then device.

**User runs:** `cd android && ./gradlew :app:assembleDebug`

**Manual device check:**
1. Play an episode for ~30s, pause.
2. Seek back 15s and play — the look-back range plays with no rebuffer (served from cache).
3. Optionally confirm `<app cacheDir>/media` grows after playback.

**Commit:** `android: cache streamed audio on disk with episode-id cache key`

---

## Feature C — Seek-to-local-progress

### C1. Extract a pure resume-seek decision helper (TDD)

The seek-on-transition logic has one real decision: *given a cached local progress and the played
flag, what position do we seek to immediately?* Extract it as a pure function so it's unit-testable
without the player.

**New file:** `android/app/src/main/kotlin/cast/android/service/ResumeSeek.kt`

```kotlin
package cast.android.service

/**
 * Position to seek to *immediately* on media-item transition, from device-local cached progress —
 * a head-start so playback doesn't visibly jump while the authoritative server position is fetched.
 *
 * The server remains the source of truth: the caller MUST still run the WebSocket `get` reconcile,
 * which re-seeks to the server value when it arrives. Returns null when there's nothing useful to
 * seek to (no cached progress, or the episode is already played → start from 0 anyway).
 */
fun localResumePositionMs(cachedProgressMs: Long?, played: Boolean): Long? = when {
    played -> null
    cachedProgressMs == null -> null
    cachedProgressMs <= 0L -> null
    else -> cachedProgressMs
}
```

**Test first** — `android/app/src/test/kotlin/cast/android/service/ResumeSeekTest.kt`:

```kotlin
package cast.android.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeSeekTest {

    @Test fun `no cached progress means no local seek`() =
        assertNull(localResumePositionMs(null, played = false))

    @Test fun `zero or negative progress means no local seek`() {
        assertNull(localResumePositionMs(0L, played = false))
        assertNull(localResumePositionMs(-5L, played = false))
    }

    @Test fun `played episode never gets a local seek`() =
        assertNull(localResumePositionMs(60_000L, played = true))

    @Test fun `unfinished episode with cached progress seeks to it`() =
        assertEquals(60_000L, localResumePositionMs(60_000L, played = false))
}
```

**User runs:** `cd android && ./gradlew :app:testDebugUnitTest --tests "cast.android.service.ResumeSeekTest"`

**Commit:** `android: add pure resume-seek decision helper`

---

### C2. Persist per-episode progress to DataStore at existing sync points (wiring)

Whenever we already send a progress `update` over the WebSocket, also stash that progress in
DataStore keyed by episode id. This is the local cache that C3 reads on transition.

**`PlaybackService`** — add a per-episode key builder and a write helper:

```kotlin
private fun progressKey(episodeId: String) = longPreferencesKey("progress_$episodeId")

private fun cacheProgress(episodeId: String, progressMs: Long) {
    if (progressMs <= 0L) return
    libraryScope.launch {
        runCatching { dataStore.edit { it[progressKey(episodeId)] = progressMs } }
    }
}
```
(Add `import androidx.datastore.preferences.core.longPreferencesKey`.)

Call `cacheProgress` everywhere we send an `update`:
- in `startProgressSync`'s loop, right before/after the `sendWs(... "update" ...)`:
  ```kotlin
  val progressMs = mediaSession?.player?.currentPosition ?: break
  cacheProgress(episodeId, progressMs)
  sendWs("""{"type":"update","episodeId":"$episodeId","progressMs":$progressMs}""")
  ```
- in `onPlayWhenReadyChanged`'s pause branch:
  ```kotlin
  val progressMs = mediaSession?.player?.currentPosition ?: 0L
  cacheProgress(episodeId, progressMs)
  sendWs("""{"type":"update","episodeId":"$episodeId","progressMs":$progressMs}""")
  ```

When an episode finishes (`STATE_ENDED`) or is removed as next-in-queue, clear its cached progress so
a replay doesn't get a stale head-start. In `onPlaybackStateChanged`'s `STATE_ENDED` branch:
```kotlin
currentEpisodeId?.let {
    sendWs("""{"type":"ended","episodeId":"$it"}""")
    clearCachedProgress(it)
}
```
with:
```kotlin
private fun clearCachedProgress(episodeId: String) {
    libraryScope.launch { runCatching { dataStore.edit { it.remove(progressKey(episodeId)) } } }
}
```

> **Not unit-tested** (DataStore + service). The *decision* is already tested in C1; this task is
> plumbing verified by the C3 device check.

**Commit:** `android: cache per-episode progress in DataStore at sync points`

---

### C3. Seek to cached progress immediately on transition (wiring; manual-verified)

In `onMediaItemTransition`, read the cached progress and seek to it **before** the server round-trip
— without touching `episodeStarted`, so `states.collect` still reconciles to the server value.

**`PlaybackService.onMediaItemTransition`:**
```kotlin
override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    Log.d(TAG, "onMediaItemTransition: mediaId=${mediaItem?.mediaId} reason=$reason")
    currentEpisodeId = mediaItem?.mediaId
    episodeStarted = false
    pushWidgetState(mediaSession?.player?.isPlaying ?: false)
    val episodeId = currentEpisodeId ?: return
    // Head-start seek from local cache so playback doesn't jump while we fetch the server position.
    // Does NOT set episodeStarted: the WS `get` reconcile (states.collect) still re-seeks to the
    // authoritative server value when it arrives. Server stays the source of truth.
    serviceScope.launch {
        val cached = runCatching { dataStore.data.first()[progressKey(episodeId)] }.getOrNull()
        if (currentEpisodeId != episodeId || episodeStarted) return@launch
        localResumePositionMs(cached, played = false)?.let { mediaSession?.player?.seekTo(it) }
    }
    // Remember it so Auto/Bluetooth can resume after the service is killed (onPlaybackResumption).
    libraryScope.launch { runCatching { dataStore.edit { it[LAST_EPISODE_ID] = episodeId } } }
    sendWs("""{"type":"get","episodeId":"$episodeId"}""")
}
```

> **Race guards:** the `currentEpisodeId != episodeId || episodeStarted` recheck after the suspend
> point prevents seeking the wrong episode (fast skips) or fighting a reconcile that already landed.
> We pass `played = false` because a played episode wouldn't be the resume target; the server
> reconcile authoritatively handles the played-→-seek-0 case via its own `if (state.played) 0L`.

**Not unit-tested** (player + DataStore + service). Decision covered by C1.

**User runs:** `cd android && ./gradlew :app:assembleDebug`

**Manual device check:**
1. Play an episode to ~2:00, pause (caches progress), kill & relaunch / replay the episode.
2. On play, audio resumes at ~2:00 **immediately** with no visible jump-from-0.
3. Change progress from the webapp, replay on Android: it still lands on the server value (reconcile
   wins over the local head-start).

**Commit:** `android: seek to cached local progress instantly on transition`

---

## Out of scope (deferred — see spec)

- Room read-through cache (cross-launch persistence; this plan's caches are in-memory + DataStore).
- Shared download cache + two-region eviction split (spec §3); queue auto-download (spec §4).
- Backend domain-decision endpoints / moving leaked logic to the Pi (spec §5).
- Offline outbox for writes; server last-writer-wins fix (spec §6 + `project_android_offline_outbox`
  memory). **Writes here still require connectivity.**

## Landing order

Features are independent and can land in any order, but recommended sequence is **A → C → B**:
A is the most visible win and lowest risk; C builds confidence in the DataStore/seek path; B is the
heaviest framework wiring and benefits from a known-good build before it.

## Verification summary (all commands run by the user)

| Stage | Command |
|-------|---------|
| Unit (pure logic) | `cd android && ./gradlew :app:testDebugUnitTest` |
| Build | `cd android && ./gradlew :app:assembleDebug` |
| Install on device | `cd android && ./gradlew :app:installDebug` |

Manual device checks as noted per feature.
