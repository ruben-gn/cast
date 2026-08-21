# "Listening" podcast status — design

**Date:** 2026-06-07
**Status:** implemented

## Summary

Add a manual per-podcast **"Listening"** status. Podcasts marked as Listening sort
first in the catalog, and the Recent episode list can be filtered to show only
episodes from Listening podcasts (on by default, persistent). The feature spans
the backend, JSON API, webapp, and Android app.

## Decisions

- **Name:** "Listening" everywhere (UI label, domain field, endpoints, setting).
- **Manual toggle**, defaulting to `true` when a feed is added.
- **Recent filter:** persistent, server-side setting `recentListeningOnly`,
  default `true`.
- **Scope:** backend + API + webapp + Android.

## 1. Domain & persistence

- `Podcast` gains `listening: Boolean`.
- `AddFeed` sets `listening = true` for new feeds.
- SQLite `podcasts` table gets `listening INTEGER NOT NULL DEFAULT 1`, added
  idempotently so existing rows become Listening.
- `PodcastCatalog` gains `suspend fun setListening(id: PodcastId, listening: Boolean)`.
- `findAll()` orders **Listening rows first**, preserving the current secondary
  order within each group.

## 2. Use cases & API

- Two use cases mirroring the existing `MarkPlayed` / `MarkUnplayed` precedent:
  - `StartListening(catalog)` → `catalog.setListening(id, true)`
  - `StopListening(catalog)` → `catalog.setListening(id, false)`
  - Each returns whether the podcast existed (for 404 vs 204), like `RemovePodcast`.
  - The port stays a single `setListening(id, listening)`; the split is only at
    the use-case layer.
- `ListPodcasts` unchanged in code — ordering lives in `findAll()`.
- `PodcastApi`:
  - `POST {id}/listening` → `StartListening` → 204 / 404
  - `DELETE {id}/listening` → `StopListening` → 204 / 404
- `PodcastSummaryDto` & `PodcastDetailDto`: add `listening: Boolean`.
- Settings:
  - Add `recentListeningOnly: Boolean` to `Settings`, `SettingsDto`,
    `SQLiteSettingsPersistence` (new column, default `1`), and `GET/PUT /api/settings`.
- `/episodes/recent` (`EpisodeApi`): already builds a `podcasts` map. When
  `getSettings().recentListeningOnly` is `true`, filter episodes to those whose
  podcast is `listening`. (`GetSettings` injected into the route.)

## 3. Clients

### Webapp (`server.tsx` proxy + `components/`)

- `PodcastList.tsx`: render a "Listening" badge/toggle per podcast; catalog
  arrives sorted Listening-first. Toggle hits new proxy routes
  `app.post('/api/podcasts/:id/listening')` + `app.delete(...)`, HTMX-swapping
  the list.
- `PodcastDetail.tsx`: a "Listening" toggle in the detail view (same routes).
- `SettingsPage.tsx`: add a "Recent shows only from Listening" switch alongside
  `hidePlayed`; existing `GET/POST /settings` flow extends to carry
  `recentListeningOnly`.
- `types.ts` / `generated/`: `listening` and `recentListeningOnly` flow in via the
  shared-models `TypeScriptGenerator` DTOs.

### Android

- Catalog (`CatalogScreen` + VM): show Listening state; toggle action calling new
  `CastApiService` endpoints. List arrives pre-sorted.
- `PodcastDetailScreen`: Listening toggle near the existing remove-podcast action.
- `RecentScreen` / `RecentViewModel`: no client filtering — backend filters per
  `recentListeningOnly`.
- `SettingsScreen` + `SettingsViewModel` + `Settings` model/repo: add the
  "recent listening only" switch.
- `CastApiService` + `PodcastRepository`: add `setListening(id, Boolean)`.

## 4. Testing

Kotest `DescribeSpec`, functional describe names (no endpoint paths).

- **Domain** (`PodcastDomainTests` + `FakePersistence`): new feed defaults to
  `listening = true`; `StartListening`/`StopListening` flip the flag; `findAll`
  returns Listening podcasts first. `FakePersistence` gains `setListening` +
  ordering.
- **API** (`PodcastApiTest`): `POST {id}/listening` → 204 + flag set; `DELETE` →
  204 + cleared; both → 404 for unknown id; DTOs include `listening`.
- **Recent filtering** (`EpisodeApiTest`): `recentListeningOnly = true` excludes
  episodes from non-Listening podcasts; `false` includes them.
- **Settings** (`SettingsApiTest` + persistence): `recentListeningOnly`
  round-trips through `GET`/`PUT` and SQLite, defaulting `true`.
- **Integration** (`SQLitePodcastPersistenceIntegrationTest`): `listening` column
  persists, defaults `1` for the idempotent column-add, `findAll` ordering holds.
  Keep contract-style where practical (DB migration is planned).
- Android tests where existing VM/repo tests cover the touched classes.
