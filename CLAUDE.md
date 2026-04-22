# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew build          # compile + test
./gradlew run            # start server on :8080
./gradlew test           # run all tests

# Run a specific test class
./gradlew test --tests "podcast.PodcastApiTest"
./gradlew test --tests "podcast.core.PodcastDomainTest"
./gradlew test --tests "podcast.adapters.RssFeedInfoProviderTest"
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
    web/view/         — SSR HTML views at /podcasts using kotlinx-html + HTMX
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

### HTMX / SSR

The view layer (`PodcastView.kt`) checks the `HX-Request` header on every request. If present, it returns a bare HTML fragment (a `<div id="content-container">`). Otherwise it returns the full page via `call.respondHtml { layout(...) { ... } }`.

### Testing conventions

- Test framework: **Kotest** (`DescribeSpec` style) with JUnit 5 runner
- **Domain tests** (`PodcastDomainTests.kt`): instantiate use cases directly, pass `FakePersistence` and a lambda `FeedInfoProvider` stub — no Ktor involved
- **API tests** (`PodcastApiTest.kt`): use `testApplication { }` from `ktor-server-test-host`; inject `FakePersistence` and `MockEngine` for the HTTP client
- **Integration tests** (`SQLitePodcastPersistenceIntegrationTest.kt`): hit a real (in-process) SQLite database
- `FakePersistence` lives in `src/test/kotlin/podcast/fakes/` and is the shared in-memory stand-in for `PodcastPersistence`
