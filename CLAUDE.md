# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Workflow

Develop directly on `main`. This project has no branching strategy — do **not** create feature branches. Commit to `main` and push.

## Modules

| Module | Description |
|--------|-------------|
| `core/` | Kotlin backend — domain + adapters (Ktor, SQLite, RSS) |
| `shared-models/` | API DTOs; a Gradle dependency for Android, generated into `webapp/generated/api.ts` for the webapp |
| `webapp/` | SSR web frontend (Bun + Hono + HTMX, TypeScript/JSX) |
| `android/` | Native Android app (Jetpack Compose, Media3, Android Auto) |

## Commands

```bash
# Backend (run from repo root)
./gradlew build          # compile + test
./gradlew run            # start server on :8100
./gradlew test           # run all tests
./gradlew test --tests "podcast.core.PodcastCoreTests"   # a single test class

# Android (run from android/ directory or via IDE)
cd android && ./gradlew assembleDebug    # build debug APK
cd android && ./gradlew installDebug     # build + install on connected device
cd android && ./gradlew test             # unit tests

# Webapp (run from webapp/)
bun run server.tsx          # start on :3000, proxies to Kotlin on :8100
bun --watch run server.tsx  # dev mode with auto-reload
```

The android module uses `jvmToolchain(21)` to match `shared-models` — don't drop it to 17, the tests load Java 21 class files and will fail with `UnsupportedClassVersionError`.

## Architecture

**Ports & Adapters (Hexagonal)**, one package per domain under `core/src/main/kotlin/`: `podcast`, `playback`, `queue`, `settings`, `series`. Each holds `core/` (use cases, `models/`, `ports/`) and `adapters/` (SQLite persistence, RSS). Alongside them: `api/` (Ktor routes, one file per domain), `application/` (use cases that span domains, e.g. `GetEpisodeDetail`), `shared/`, `configuration/` (SQLite connection).

`src/main/kotlin/Application.kt` wires it: `installDatabase()`, then one `install*Module()` per domain, then `installRoutes()`.

`ArchitectureTests.kt` (Konsist) enforces the boundaries — domain use cases may not import other domains, and neither the `api` nor `application` layer may import ports. If you cross a layer, that test fails.

### Dependency Injection

Ktor's built-in `ktor-server-di` plugin is used throughout.

- Register: `dependencies { provide<MyType> { ... } }`
- Resolve imperatively (setup time): `resolve<MyType>()`
- Resolve by delegation (in route handlers): `val x: MyType by dependencies`

Tests override a port by providing its fake *before* calling the `install*Module()` function.

### Webapp (Bun + Hono)

`KOTLIN_API` env var controls where the webapp calls the backend (default: `http://localhost:8100`).

The view layer (`server.tsx`) checks the `HX-Request` header on every request. If present, it returns a bare HTML fragment (`<div id="content-container">`). Otherwise it returns the full page via `<Layout>`.

The WebSocket at `/api/playback` is proxied through Bun to the Kotlin backend. Messages arriving while the Kotlin connection is still opening are buffered and flushed on `onopen`.

### Docker

```bash
docker compose up          # start api (:8100) + webapp (:3000)
docker compose up --build  # rebuild images first
```

`Dockerfile.api` builds the fat jar inside the image (multi-stage) and runs it as a non-root user.
`webapp/Dockerfile` installs deps with `bun install` and runs `server.tsx` directly — no compilation.

### Testing conventions

- **Kotest** (`DescribeSpec` style) with the JUnit 5 runner. `describe`/`it` blocks get functional names ("groups matching episodes under the series name"), never endpoint paths.
- **Domain tests** (`*CoreTests.kt`): instantiate use cases directly against a fake — no Ktor involved.
- **Port contracts** (`*Contract.kt` in the test-source `*.core.ports` packages): a shared `TestFactory` every adapter must satisfy. Both the SQLite adapters (`SQLite*IT.kt`) and the fakes include it, so use-case tests stay valid if the persistence layer is swapped.
- **Fakes** live per domain in `core/src/test/kotlin/<domain>/fakes/`.

### Kotlin conventions

No `= null` parameter defaults — every null is explicit at the callsite. The exceptions are wire-compat fields on DTOs in `shared-models`, where a new client must deserialize an older server's JSON.
