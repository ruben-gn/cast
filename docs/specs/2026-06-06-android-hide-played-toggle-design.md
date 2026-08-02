# Android "Hide played episodes" toggle — design

**Date:** 2026-06-06
**Status:** Approved, ready for implementation plan

## Problem

The "Hide played episodes" switch in the Android Settings screen does nothing
visible. Flipping it persists the preference to local DataStore but played
episodes still appear in every list.

## Root cause

The filtering already exists — on the **server**, gated on a single global
`hide_played` setting:

- `GET`/`PUT /api/settings` exist (`core/.../api/SettingsApi.kt`), backed by one
  global row keyed `"hide_played"` (`SQLiteSettingsPersistence.kt`).
- `GetPodcastDetail` already drops played episodes when the setting is on
  (`application/usecase/GetPodcastDetail.kt:19,27`).
- The **webapp toggle already works** through this path
  (`SettingsPage.tsx` → `POST /settings` → `PUT /api/settings`).
- Android's `CastApiService` already declares `getSettings()` / `updateSettings()`.

The gap is in the Android client only: `SettingsRepositoryImpl.updateSettings()`
writes `hidePlayed` **only to local DataStore** (`:41`) and never calls the API.
So the Android toggle never reaches the server, the server filter never sees a
change, and episodes are never hidden.

Sharper consequence of the global setting: if the user ever toggled hide-played
in the *webapp*, their Android detail lists are **already** filtered — the
Android switch simply can't control it.

## Decision

Server-side filtering, reusing the existing **shared global** setting (chosen
over a per-device query param). Toggling on the phone also changes it for the
webapp, and vice versa — one shared value across all clients. This is the
existing webapp model and requires the least code (no server changes).

## Design (Android only — the server is already done)

**Principle:** `hidePlayed` becomes server-owned (the source of truth that
drives filtering). `serverUrl` stays local — it must, since it's needed to reach
the server. DataStore keeps a local *cache* of `hidePlayed` for instant toggle
display.

1. **`SettingsRepositoryImpl`** — inject `CastApiService`:
   - `updateSettings()`: keep writing DataStore + `baseUrlInterceptor` as today,
     **and** push to the server via
     `api.updateSettings(SettingsDto(hidePlayed = settings.hidePlayed))`.
   - Add `refresh()`: call `api.getSettings()` and write the returned
     `hidePlayed` into DataStore, so the toggle reflects the shared global value
     (including changes made from the webapp).

2. **`SettingsRepository`** interface: add `suspend fun refresh()`.

3. **`SettingsViewModel`**: call `refresh()` in `init` so opening Settings shows
   the true server state.

4. **No UI / PodcastDetail changes.** When the setting is on, the server returns
   the podcast detail with played episodes already removed; the list renders
   fewer rows. `getPodcast` is uncached (`PodcastRepositoryImpl.kt:26`), so the
   next load reflects the change with no cache invalidation.

## Error behavior

A settings read/write requires connectivity, consistent with v1 `markPlayed`
(`.orThrow()`) and the deferred offline-outbox decision. If the `PUT` fails,
surface the error rather than silently diverging from the server.

## Known limitation (v1, won't fix)

The optimistic `togglePlayed` in `PodcastDetailViewModel` marks an episode played
in place, so a just-marked episode stays visible (as played) until the next
load — it doesn't know about the server setting. Acceptable and documented.

## Testing

- `SettingsRepositoryImplTest`:
  - `updateSettings` pushes a `SettingsDto` with the correct `hidePlayed` to the
    API.
  - `refresh` writes the server's `hidePlayed` value into settings.
- `FakeCastApiService` already declares `getSettings`/`updateSettings` as
  `TODO()` — make them configurable/recordable for the tests.

## Out of scope

- Per-device hide-played semantics (query-param approach).
- Recent screen (already drops played/completed episodes).
- Offline write queueing (separately deferred).
