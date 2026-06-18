# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

Develop directly on `main`. This project has no branching strategy — do **not** create feature branches. Commit to `main` and push.

## Modules

| Module | Description |
|--------|-------------|
| `core/` | Shared domain + adapters (Ktor server, SQLite, RSS) |
| `webapp/` | SSR web frontend (Bun + Hono + HTMX, TypeScript/JSX) |
| `android/` | Native Android app (Jetpack Compose, Media3, Android Auto) |

## Commands

```bash
# Backend (run from repo root)
./gradlew build          # compile + test
./gradlew run            # start server on :8100
./gradlew test           # run all tests

# Run a specific test class
./gradlew test --tests "podcast.PodcastApiTest"
./gradlew test --tests "podcast.core.PodcastDomainTest"
./gradlew test --tests "podcast.adapters.RssFeedInfoProviderTest"

# Android (run from android/ directory or via IDE)
cd android && ./gradlew assembleDebug    # build debug APK
cd android && ./gradlew installDebug     # build + install on connected device
cd android && ./gradlew test            # unit tests
cd android && ./gradlew connectedAndroidTest  # instrumented tests
```

## Architecture

The project follows a **Ports & Adapters (Hexagonal)** pattern under the `podcast` package:

```
podcast/
  core/               — domain use cases and port interfaces
    AddFeed.kt        — subscribe to an RSS feed URL
    ListPodcasts.kt   — list all subscribed podcasts
    GetPodcast.kt     — retrieve a single podcast with episodes
    model/            — Podcast, Episode data classes
    port/             — PodcastPersistence, FeedInfoProvider interfaces (EpisodeInfo, FeedInfo DTOs)
  adapters/
    web/api/          — JSON REST API at /api/podcasts (GET, POST, GET /{id})
    persistence/      — SQLitePodcastPersistence (production), InMemoryPodcastPersistence
    RssFeedInfoProvider.kt — fetches and parses RSS XML via Ktor HTTP client
configuration/
  SQLite.kt           — opens podcasts.db, creates tables, provides Connection via DI
```

`Application.kt` wires it all together: `installDatabase()` → `installPodcastModule()`.

### Dependency Injection

Ktor's built-in `ktor-server-di` plugin is used throughout.

- Register: `dependencies { provide<MyType> { ... } }`
- Resolve imperatively (setup time): `resolve<MyType>()`
- Resolve by delegation (in route handlers): `val x: MyType by dependencies`

`installPodcastModule()` registers all use cases and adapters. Tests override `PodcastPersistence` by providing `FakePersistence` *before* calling `installPodcastModule`.

### Webapp (Bun + Hono)

```bash
# Webapp (run from webapp/ directory)
cd webapp && bun run server.tsx        # start on :3000, proxies to Kotlin on :8100
cd webapp && bun --watch run server.tsx  # dev mode with auto-reload
```

`KOTLIN_API` env var controls where the webapp calls the backend (default: `http://localhost:8100`).

The view layer (`server.tsx`) checks the `HX-Request` header on every request. If present, it returns a bare HTML fragment (`<div id="content-container">`). Otherwise it returns the full page via `<Layout>`.

The WebSocket at `/api/playback` is proxied through Bun to the Kotlin backend. Messages arriving while the Kotlin connection is still opening are buffered and flushed on `onopen`.

### Docker

```bash
docker compose up          # start api (:8100 internal) + webapp (:3000 exposed)
docker compose up --build  # rebuild images first
```

`Dockerfile.api` builds the fat jar inside the image (multi-stage) and runs it as a non-root user.
`webapp/Dockerfile` installs deps with `bun install` and runs `server.tsx` directly — no compilation.

### Testing conventions

- Test framework: **Kotest** (`DescribeSpec` style) with JUnit 5 runner
- **Domain tests** (`PodcastDomainTests.kt`): instantiate use cases directly, pass `FakePersistence` and a lambda `FeedInfoProvider` stub — no Ktor involved
- **API tests** (`PodcastApiTest.kt`): use `testApplication { }` from `ktor-server-test-host`; inject `FakePersistence` and `MockEngine` for the HTTP client
- **Integration tests** (`SQLitePodcastPersistenceIntegrationTest.kt`): hit a real (in-process) SQLite database
- `FakePersistence` lives in `src/test/kotlin/podcast/fakes/` and is the shared in-memory stand-in for `PodcastPersistence`
