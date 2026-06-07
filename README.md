# Cast

A self-hosted podcast player focused on seamless cross-device playback continuity. Start an episode on the web, pick it up on Android — exactly where you left off.

## Features

- **Playback sync** — position is persisted server-side and shared across all clients in real time via WebSocket
- **RSS subscriptions** — add any podcast by feed URL; metadata and episodes are fetched automatically
- **Queue management** — build and reorder a play queue across devices
- **OPML import** — bulk-import subscriptions from another app
- **Android app** — native Jetpack Compose player with Android Auto support and a home screen widget
- **Background refresh** — feeds update automatically via WorkManager

**Planned:** in-app podcast search and discovery.

## Architecture

Cast is three modules that talk to each other over HTTP:

```
core/      Kotlin backend — REST API + WebSocket, SQLite persistence, RSS parsing
webapp/    SSR web frontend — Bun + Hono + HTMX, proxies to the backend
android/   Native Android app — Media3, Jetpack Compose, Retrofit
```

The backend follows a **Ports & Adapters (Hexagonal)** structure: domain use cases in `core/` are isolated from I/O behind port interfaces, with adapters for SQLite, RSS, and the HTTP API wired together at startup. The webapp and Android app are independent clients that consume the same REST API.

## Self-hosting

Prerequisites: Docker and Docker Compose.

```bash
git clone https://github.com/your-username/cast.git
cd cast
docker compose up
```

The webapp is available at `http://localhost:3000`. The API runs on `:8100` (internal only).

Data is persisted in a named Docker volume (`db-data`).

**Android:** build the APK with `cd android && ./gradlew assembleDebug` and set the server URL in `android/local.properties`:

```
DEFAULT_SERVER_URL=http://your-host:3000
```

## Built with Claude Code

The backend was designed and largely written by hand — architecture decisions, domain modelling, SQL schema, test structure. Once that baseline was set, Claude Code joined in as a supervised coding assistant: generating boilerplate, suggesting implementations, catching bugs, and explaining trade-offs. Everything it produced was reviewed before it landed.

The webapp and Android app are a different story: both are **fully AI-generated**. The workflow was conversational — describe a feature, review the output, redirect as needed, iterate. No webapp or Android code was written by hand.

This project is partly a working app and partly an experiment in what that kind of collaboration actually produces at scale.
