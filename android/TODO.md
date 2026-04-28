# Cast Android — Implementation Plan

## Stack
- Kotlin 2.3 + K2 compiler
- minSdk / targetSdk / compileSdk = 36 (Android 16)
- Jetpack Compose + Material 3
- Media3 (ExoPlayer + MediaLibraryService) — background audio + Android Auto
- Ktor client + Kotlin Serialization — networking
- Hilt — dependency injection
- Coil 3 — image loading
- DataStore — settings persistence
- Navigation Compose (type-safe routes)
- Coroutines + Flow throughout

## Server
- Tailscale IP: `http://cast.local:8100`
- Configurable via Settings screen (stored in DataStore)

## API
- `GET  /api/podcasts`       → list of podcasts
- `GET  /api/podcasts/{id}`  → podcast detail + episodes
- `WS   /api/playback`       → cross-device playback sync
  - send: `{"type":"get","episodeId":"<id>"}`
  - recv: `{"type":"state","episodeId":"<id>","progressMs":<ms>}`
  - send: `{"type":"update","episodeId":"<id>","progressMs":<ms>}`
- Shared DTOs live in `:shared-models` (KMP module)

---

## Phase 1 — Shared models KMP module ✅
- [x] 1.1 Create `shared-models/` with KMP Gradle config (jvm target)
- [x] 1.2 Move DTOs to `commonMain`: PodcastSummaryDto, PodcastDetailDto, EpisodeDto, AddPodcastRequest, PlaybackStateResponse
- [x] 1.3 Update `core` to depend on `:shared-models`; remove old DTO definitions
- [x] 1.4 Server build + tests green

## Phase 2 — Android module scaffold
- [ ] 2.1 Create `android/build.gradle.kts`, wire into root `settings.gradle.kts`
- [ ] 2.2 Kotlin 2.3 + K2, AGP 8.x, compileSdk/targetSdk/minSdk = 36
- [ ] 2.3 Version catalog: Compose BOM, Material 3, Media3, Ktor client + WebSockets, Coil 3, DataStore, Navigation Compose, Hilt
- [ ] 2.4 Depend on `:shared-models` (add `androidTarget()` to shared-models)
- [ ] 2.5 `MainActivity` — `enableEdgeToEdge()`, Hilt, Compose scaffold, NavHost

## Phase 3 — Network + data layer
- [ ] 3.1 Ktor HTTP client — base URL `http://cast.local:8100`, configurable
- [ ] 3.2 `PodcastRepository` — list podcasts, get podcast detail
- [ ] 3.3 DataStore for settings (server base URL)
- [ ] 3.4 WebSocket client — `getPosition(episodeId)` and `updatePosition(episodeId, ms)` suspend funs

## Phase 4 — Background media playback
- [ ] 4.1 `CastMediaLibraryService` extending Media3 `MediaLibraryService`
- [ ] 4.2 `ExoPlayer` + `MediaSession` inside the service
- [ ] 4.3 `DefaultMediaNotificationProvider` — lockscreen + notification controls
- [ ] 4.4 Foreground service declaration + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permission
- [ ] 4.5 `MediaController` in UI — connects to service via `SessionToken`
- [ ] 4.6 On episode start: WebSocket `get` → seek ExoPlayer to saved position
- [ ] 4.7 Periodic sync: every 5s during playback → WebSocket `update`

## Phase 5 — UI screens
- [ ] 5.1 `PodcastListScreen` — artwork grid, pull-to-refresh
- [ ] 5.2 `PodcastDetailScreen` — header, episode list (title / date / duration)
- [ ] 5.3 Persistent mini-player bar when audio is active
- [ ] 5.4 `NowPlayingScreen` — large artwork, scrub bar, play/pause/+30s/−15s
- [ ] 5.5 `SettingsScreen` — server URL field + connection test
- [ ] 5.6 Shared element transitions, edge-to-edge insets

## Phase 6 — Android Auto
- [ ] 6.1 Manifest: `automotive_app_desc.xml` declaring `media` category
- [ ] 6.2 `onGetLibraryRoot` — browsable root
- [ ] 6.3 `onGetChildren`: root → podcasts (browsable) → episodes (playable MediaItems)
- [ ] 6.4 `onPlaybackResumption` — Auto resumes last episode
- [ ] 6.5 Test with Desktop Head Unit (DHU) emulator

## Phase 7 — Polish
- [ ] 7.1 Offline error states + retry
- [ ] 7.2 WebSocket reconnect with exponential backoff
- [ ] 7.3 Adaptive icon + splash screen

## Phase 8 — Subscriptions (post-MVP)
- [ ] 8.1 `AddPodcastScreen` — RSS URL → `POST /api/podcasts`
- [ ] 8.2 Preview feed name/artwork on success
- [ ] 8.3 FAB on `PodcastListScreen`
