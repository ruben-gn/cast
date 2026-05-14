# Phase 3 — Android App

## Goal

A native Android app that consumes the same backend as the webapp.
It must feel like a first-class podcast client: background playback,
lock-screen controls, Android Auto support, and seamless cross-device
sync with the webapp via the shared backend.

## Constraints and decisions

- **Target: Android 16** (API 36), minimum SDK TBD based on device.
- **UI: Jetpack Compose** throughout. No XML layouts.
- **Playback: Media3 (ExoPlayer)**. MediaSession for OS integration.
- **Architecture: hexagonal**, mirroring the backend. Domain use cases
  are pure Kotlin with no Android dependencies; adapters wrap the
  Android/network layer. This makes domain logic testable without a device.
- **No authentication on the backend.** The app connects to a configured
  server URL (self-hosted, personal use). Basic auth or a bearer token
  can be added later; it is explicitly out of scope for Phase 3.
- **Cross-device sync is free.** The backend already persists playback
  state. Playing on Android syncs to the backend via WebSocket; the
  webapp reads the same state on next load. No extra work needed.
- **The Android module was removed** (commit 1f413ca). This is a fresh
  start in a new `android/` directory.

---

## Backend changes required for Phase 3

### 1. No episode-by-ID endpoint exists

`GET /api/queue/detail` (Phase 2) solves episode lookup for the queue.
For other Android needs (e.g. deep-linking to an episode) a direct
`GET /api/episodes/{id}` may be useful but is not strictly required if
the app always navigates through the podcast detail.

- [ ] Evaluate whether `GET /api/episodes/{id}` is needed during
  implementation; add only if a concrete use case arises

### 2. No server-sent events or polling for podcast list updates

The webapp relies on the 5-minute background refresh in `UpdateFeeds`.
The Android app will do the same — no push from server to client needed.

---

## Android module setup

- [ ] Create `android/` module, add to `settings.gradle.kts`
- [ ] Configure `build.gradle.kts`:
  - `compileSdk 36`, `targetSdk 36`
  - Jetpack Compose BOM (latest)
  - Media3 ExoPlayer + MediaSession
  - Ktor client (reuse same HTTP library as backend) or OkHttp
  - Kotlin coroutines + Flow
  - Hilt for dependency injection
- [ ] Set up baseline project structure:
  ```
  android/src/main/kotlin/cast/android/
    domain/          — use cases, port interfaces (no Android deps)
    adapters/
      api/           — Ktor/OkHttp HTTP client
      playback/      — Media3 adapter
      persistence/   — DataStore for local config
    ui/              — Compose screens and ViewModels
  ```
- [ ] Verify build: `./gradlew :android:compileDebugKotlin`
  (Note: `assembleDebug` requires AAPT2 which is broken on this machine;
  use `compileDebugKotlin` to verify Kotlin compilation)

---

## Feature checklist

### Server configuration

The user must be able to point the app at their Cast server.

- [ ] `ServerConfig` data class (url: String)
- [ ] `DataStore<Preferences>` adapter for persisting config
- [ ] Setup screen shown on first launch (or when no server is configured)
- [ ] Settings screen accessible from main navigation to change server URL
- [ ] Validate URL on save (attempt `GET /api/podcasts`, show error if unreachable)

### Podcast list

- [ ] `GetPodcasts` domain use case (calls `GET /api/podcasts`)
- [ ] HTTP adapter implementation
- [ ] `PodcastListViewModel` with `StateFlow<List<Podcast>>`
- [ ] `PodcastListScreen`:
  - Grid of podcast cards (image + name)
  - Pull to refresh
  - Loading and error states
  - Empty state with prompt to add via webapp

### Podcast detail

- [ ] `GetPodcastDetail` domain use case (calls `GET /api/podcasts/{id}`)
- [ ] `PodcastDetailViewModel`
- [ ] `PodcastDetailScreen`:
  - Cover art, title, episode count
  - Scrollable episode list
  - Each episode: title, publish date, duration, played badge, progress bar
  - Play button per episode

### Audio playback

This is the core of the app. Media3 handles the heavy lifting but
the wiring requires care.

- [ ] `PlaybackService` (foreground service, `MediaSessionService`)
  - Extends `MediaSessionService` from Media3
  - Owns the `ExoPlayer` instance
  - Exposes `MediaSession` for OS and Android Auto integration
- [ ] `MediaControllerAdapter` — app-side interface to the service via
  `MediaController` (Jetpack Media3 pattern)
- [ ] `PlaybackViewModel` — bridges `MediaControllerAdapter` to UI
- [ ] Set audio URL, title, artwork on `MediaItem`
- [ ] Player bar in `MainActivity` scaffold (always visible when playing):
  - Artwork, title, play/pause, seek bar
- [ ] Handle audio focus (loss, transient loss, duck)
- [ ] Handle headphone disconnect (pause)

### Playback state sync

The backend WebSocket protocol (`{type: "update"}`, `{type: "ended"}`,
`{type: "get"}`) is the same as the webapp.

- [ ] `PlaybackSyncAdapter` — manages a WebSocket connection to
  `/api/playback` using Ktor client WebSocket
- [ ] On play: send `{type: "get", episodeId}` → apply resume position
- [ ] On `timeupdate` (every ~500ms of playback): send `{type: "update", ...}`
- [ ] On episode end: send `{type: "ended", episodeId}`
- [ ] Reconnect on disconnect (exponential backoff)
- [ ] Sync runs inside the `PlaybackService` so it continues in background

### Notifications and lock screen

Media3's `MediaSession` handles most of this automatically.

- [ ] Notification shows episode title, artwork, play/pause, skip controls
- [ ] Lock screen controls via `MediaSession`
- [ ] Notification channel configured for media playback

### Queue

- [ ] `GetQueue` domain use case (calls `GET /api/queue/detail` — Phase 2 backend)
- [ ] `AddToQueue` use case (`POST /api/queue/{id}/last`)
- [ ] `RemoveFromQueue` use case (`DELETE /api/queue/{episodeId}`)
- [ ] Queue screen:
  - Ordered list of episodes
  - Swipe to remove
  - Drag to reorder (`POST /api/queue/{id}/{position}`)
- [ ] "Add to queue" action on episode items (long-press or menu)
- [ ] Auto-advance: when episode ends, check queue and play next
  (same logic as webapp — `PlaybackService` handles this)
- [ ] Queue tab or button in bottom navigation

### Android Auto

Media3's `MediaSession` provides the foundation; Auto needs a
content browser and playback controls.

- [ ] Declare `androidx.car.app.CATEGORY_MEDIA_APP` in manifest
- [ ] Implement `MediaLibraryService` (upgrade from `MediaSessionService`
  or add alongside)
- [ ] Content browser returns podcast list → episode list hierarchy
- [ ] Playback controls: play/pause, skip forward 30s, skip back 15s
- [ ] Now-playing metadata (title, artwork) surfaced through `MediaSession`
- [ ] Test with Desktop Head Unit (DHU) emulator

### Navigation

- [ ] Bottom navigation: Podcasts | Queue | Settings
- [ ] `NavHost` with Compose Navigation
- [ ] Deep link support for episode URLs (to support sharing from webapp)

---

## Testing

- [ ] Domain use case unit tests (pure Kotlin, no Android)
- [ ] HTTP adapter tests with mock server (MockWebServer or Ktor mock engine)
- [ ] ViewModel tests with `TestScope` and fake adapters
- [ ] `PlaybackService` integration test with `MediaController` on emulator
- [ ] Android Auto test with DHU

---

## Definition of done

- App builds and installs on a physical Android device
- Can browse podcasts, play an episode, see resume position match webapp
- Background playback continues when app is backgrounded
- Lock screen and notification controls work
- Progress syncs to backend: playing on Android, refreshing webapp shows
  the same position
- Queue: add from Android, visible on webapp and vice versa
- Android Auto: podcast list navigable, playback controllable from head unit
