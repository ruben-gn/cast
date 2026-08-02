# Cast

A self-hosted podcast player focused on seamless cross-device playback continuity. Start an episode on the web, pick it up on Android — exactly where you left off.

## Features

- **Playback sync** — position is persisted server-side and shared across all clients in real time via WebSocket
- **RSS subscriptions** — add any podcast by feed URL; metadata and episodes are fetched automatically
- **Queue management** — build and reorder a play queue across devices; the Android player runs the queue as one playlist and advances through it
- **Recent feed** — an unplayed-first feed across all subscriptions, with mark-played / mark-unplayed and a "hide played" setting
- **Listening status** — flag the podcasts you actively follow; the catalog groups by status and Recent can be limited to them
- **Series grouping** — multi-part series collapse into one expandable row in Recent instead of flooding it, driven by per-podcast rules with name suggestions from the episode titles
- **Offline downloads** — download episodes for offline playback, with progress shown on the play button and automatic cleanup of what you've finished
- **Works offline** — playback progress and mark-played are stored locally and flushed to the server on reconnect; the server keeps the newest update per episode
- **New episode notifications** — the Android app notifies when a podcast you're listening to publishes
- **OPML import** — bulk-import subscriptions from another app
- **Android app** — native Jetpack Compose player with Android Auto support, a home screen widget, and light/dark themes
- **Background refresh** — feeds update automatically via WorkManager
- **APK releases** — every push to `main` builds a signed debug APK and publishes it as a GitHub release

**Planned:** chapter support, in-app podcast search and discovery.

## Architecture

Cast is a backend plus two clients that talk to it over HTTP:

```
core/           Kotlin backend — REST API + WebSocket, SQLite persistence, RSS parsing
shared-models/  API DTOs, shared with Android as a Gradle dependency and generated into TypeScript for the webapp
webapp/         SSR web frontend — Bun + Hono + HTMX, proxies to the backend
android/        Native Android app — Media3, Jetpack Compose, Retrofit
```

The backend follows a **Ports & Adapters (Hexagonal)** structure: domain use cases in `core/` are isolated from I/O behind port interfaces, with adapters for SQLite, RSS, and the HTTP API wired together at startup. The webapp and Android app are independent clients that consume the same REST API, and both take their request/response types from `shared-models/`, so a wire change breaks the build rather than production.

## Self-hosting

Prerequisites: Docker and Docker Compose.

```bash
git clone https://github.com/your-username/cast.git
cd cast
docker compose up
```

The webapp is available at `http://localhost:3000`; the API on `:8100`, where `/health` reports whether the database is reachable.

Data is persisted in a named Docker volume (`db-data`).

> **No authentication.** Cast is built for a single user on a trusted network — every API route is open to anyone who can reach it. Keep it on your LAN or behind a VPN; don't expose port `3000` or `8100` to the internet without putting your own auth in front.

**Android:** install the APK from the [latest release](../../releases/latest), or build it yourself with `cd android && ./gradlew assembleDebug`. Either way the app talks to the API directly, so point it at `:8100` — set the server URL in Settings, or bake in a default via `android/local.properties`:

```
cast.serverUrl=http://your-host:8100
```

## Built with Claude Code

The backend was designed and largely written by hand — architecture decisions, domain modelling, SQL schema, test structure. Once that baseline was set, Claude Code joined in as a supervised coding assistant: generating boilerplate, suggesting implementations, catching bugs, and explaining trade-offs. Everything it produced was reviewed before it landed.

The webapp and Android app are a different story: both are **fully AI-generated**. The workflow was conversational — describe a feature, review the output, redirect as needed, iterate. No webapp or Android code was written by hand.

This project is partly a working app and partly an experiment in what that kind of collaboration actually produces at scale.
