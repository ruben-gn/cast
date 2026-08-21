# Offline progress sync — design & implementation plan

Date: 2026-07-31. Status: implemented and deployed 2026-07-31.

## Goal

Progress and mark-played from offline listening must reliably reach the server, surviving
process death, without a stale device clobbering fresher progress from another device.

Two independent fixes, in order:

1. **Server**: make progress merge last-*listen*-wins instead of last-*arrival*-wins.
2. **Android**: make offline progress/ended durable (today they live only in
   `PlaybackWebSocketClient`'s in-memory `pending` queue, lost on process death) and flush
   them whenever the socket (re)opens.

Milestone 1 must land before milestone 2–3: without the server-side guard, the Android
flush would *introduce* the clobbering bug (a reconnecting phone overwriting fresher
webapp progress).

## What already works — do not rebuild

- `PlaybackProgressStore` (DataStore) caches per-episode progress every 1 s and on pause;
  survives process death; feeds the head-start seek in
  `QueuePlaybackListener.onMediaItemTransition`.
- `onServerState` only seeks *forward* past `RESUME_TOLERANCE_MS`, so a stale server
  position never rewinds local playback.
- `PlaybackWebSocketClient.send(message, coalesceKey)` queues unsent messages in memory,
  coalescing per episode, and flushes on `onOpen`. Keep this exactly as is — it still
  covers short blips and `start`/`get` messages. The durable outbox below may duplicate
  a message the in-memory queue also delivers; that is fine (all messages are idempotent,
  and the server timestamp guard discards stale ones). Do not try to deduplicate.

## Decisions (already made — do not reopen)

- Policy: **most-recent-listen-wins** (handles deliberate rewinds; furthest-progress-wins
  does not).
- The `update` WS message gains an **optional** `updatedAt` field, **epoch milliseconds**
  (`System.currentTimeMillis()` on the phone). The webapp keeps sending no timestamp;
  the server then falls back to its own clock — exactly today's behavior.
- No schema migration: `playback_state.updated_at` (TEXT, ISO-8601) already exists.
- `ended` (mark-played) stays unconditional on the server. Finishing an episode should
  win over concurrent progress. Accepted edge: none worth guarding for a single user.
- `StartPlayback`/`resetProgress` stays unconditional: `start` always carries the live
  player position, never a stale one.
- Deferred, out of scope here: queue-edit outbox (op-shaped, different design),
  WorkManager background flush (flush currently requires the service to be running),
  webapp timestamps, typed WS messages refactor.

## Repo ground rules

- Work directly on `main`, plain commit messages (no conventional-commit prefixes).
- One commit per milestone below.
- Core tests run on this machine: `./gradlew test` from the repo root.
- **Android tests/builds do NOT run on this machine** (no Android SDK). Verify by
  pushing: `.github/workflows/android.yml` runs `./gradlew --no-daemon test` and
  `assembleDebug` on every push. Watch it with `gh run watch`.

---

## Milestone 1 — server: last-listen-wins merge

### 1a. `core/src/main/kotlin/api/PlaybackApi.kt`

In the `"update"` branch, parse the optional timestamp and pass it through:

```kotlin
"update" -> {
    val progressMs = obj["progressMs"]!!.jsonPrimitive.long
    val updatedAt = obj["updatedAt"]?.jsonPrimitive?.long?.let(Instant::ofEpochMilli)
    updateProgress(episodeId = episodeId, progressMs = progressMs, updatedAt = updatedAt)
}
```

(`import java.time.Instant`.)

### 1b. `core/src/main/kotlin/playback/core/usecase/UpdateProgress.kt`

```kotlin
suspend operator fun invoke(episodeId: EpisodeId, progressMs: Long, updatedAt: Instant?) {
    state.updateProgress(episodeId, progressMs, updatedAt ?: clock.instant())
}
```

**Do not give `updatedAt` a `= null` default** — this repo bans default-null parameters;
every caller passes it explicitly. Find and update all callers (the WS route above, plus
any tests in `core/src/test/kotlin/playback/core/PlaybackCoreTests.kt`).

### 1c. `core/src/main/kotlin/playback/adapters/persistence/SQLitePlaybackState.kt`

Make `updateProgress` a conditional upsert — strictly-newer wins:

```sql
INSERT INTO playback_state (episode_id, progress_ms, updated_at, played)
VALUES (?, ?, ?, 0)
ON CONFLICT(episode_id) DO UPDATE SET
    progress_ms = excluded.progress_ms,
    updated_at = excluded.updated_at
WHERE julianday(excluded.updated_at) > julianday(playback_state.updated_at)
```

**Why `julianday()` and not string `>`**: the column holds mixed precision.
`markPlayed` writes second-precision strings (`...T10:30:00Z` via `strftime`), while
`updateProgress` writes `Instant.toString()` which includes fractional seconds when
non-zero (`...T10:30:00.500Z`). Lexicographically `"00.500Z" < "00Z"` (`.` sorts before
`Z`), so string comparison would order those *backwards*. `julianday()` parses both forms
(SQLite accepts the trailing `Z`) and compares numerically with sub-millisecond
precision. Do not "simplify" to a string comparison.

Leave `resetProgress`, `markPlayed`, `markUnplayed`, `markAllPlayed` untouched
(unconditional by design, see Decisions).

### 1d. `core/src/test/kotlin/playback/fakes/FakePlaybackPersistence.kt`

Mirror the same semantics so the shared contract passes:

```kotlin
override suspend fun updateProgress(episodeId: EpisodeId, progressMs: Long, updatedAt: Instant) {
    val existing = storage[episodeId]
    if (existing != null && !updatedAt.isAfter(existing.updatedAt)) return
    storage[episodeId] = (existing ?: PlaybackState(episodeId, progressMs, updatedAt, played = false))
        .copy(progressMs = progressMs, updatedAt = updatedAt)
}
```

### 1e. Contract tests — `core/src/test/kotlin/playback/core/ports/PlaybackPersistenceContract.kt`

Add to the existing `describe("updateProgress")` block (Kotest style; functional
describe-names, never endpoint paths). The contract runs against both the fake and the
real SQLite adapter, so these pin both:

```kotlin
it("ignores an update older than the stored state") {
    // update at 10:30, then update progress=1000 at 10:00 → stored stays 5000 @ 10:30
}

it("ignores an update with the same timestamp as the stored state") {
    // strictly-newer wins; equal timestamp keeps the existing row
}

it("applies an update with fractional-second precision newer than a whole-second stored state") {
    // stored updatedAt 2024-01-15T10:30:00Z, update at 2024-01-15T10:30:00.500Z with
    // progress 9000 → applies. This is the lexicographic-comparison regression trap.
}
```

For the third test the stored row must have a whole-second `updatedAt`
(`Instant.parse("2024-01-15T10:30:00Z")` round-trips as `...:00Z`, no fraction — that is
sufficient; no need to go through `markPlayed`).

### 1f. Use-case test — `core/src/test/kotlin/playback/core/PlaybackCoreTests.kt`

Two cases for `UpdateProgress`:
- called with `updatedAt = null` → persistence receives the (fixed test) clock's instant;
- called with an explicit `updatedAt` → persistence receives exactly that instant.

Follow the existing pattern in that file (fixed `Clock`, `FakePlaybackPersistence`).

### Verify & commit

```bash
./gradlew test
```

All green → commit to main, message like:
`Progress updates carry a listen timestamp and the server keeps the newest`

---

## Milestone 2 — Android: timestamped local progress + durable pending-ended

All paths below are under `android/app/src/`.

### 2a. `main/kotlin/cast/android/network/PlaybackWebSocketClient.kt`

Change `send` to report whether the message went out on a live socket now (`true`) or was
queued (`false`):

```kotlin
fun send(message: String, coalesceKey: String? = null): Boolean {
    val ws = webSocket
    if (connected && ws != null && ws.send(message)) return true
    synchronized(pending) {
        if (coalesceKey != null) pending.removeAll { it.coalesceKey == coalesceKey }
        pending.add(PendingMessage(coalesceKey, message))
    }
    return false
}
```

### 2b. `main/kotlin/cast/android/service/PlaybackProgressStore.kt`

Extend the interface (and the DataStore implementation):

```kotlin
interface PlaybackProgressStore {
    suspend fun cachedProgressMs(episodeId: String): Long?
    fun cacheProgress(episodeId: String, progressMs: Long, atMillis: Long)
    fun clearCachedProgress(episodeId: String)
    fun markEndedPending(episodeId: String)
    fun clearEndedPending(episodeId: String)
    suspend fun pendingSync(): PendingSync
    fun rememberLastEpisode(episodeId: String)
    fun clearLastEpisode()
}

data class PendingProgress(val episodeId: String, val progressMs: Long, val atMillis: Long)
data class PendingSync(val progress: List<PendingProgress>, val endedEpisodeIds: List<String>)
```

Implementation notes for `DataStorePlaybackProgressStore`:
- New keys, same DataStore: `progress_at_<id>` (`longPreferencesKey`) written by
  `cacheProgress` alongside the existing `progress_<id>`; `ended_pending_<id>`
  (`booleanPreferencesKey`).
- `clearCachedProgress` now also removes `progress_at_<id>` (it is called when an episode
  ends; the ended flag is managed separately, don't touch it here).
- `pendingSync()` reads `dataStore.data.first().asMap()` once and prefix-scans key names:
  - progress entries: keys starting `"progress_at_"` → strip prefix for the id, pair with
    the `progress_<id>` value. **An entry without a `progress_at_` companion (pre-existing
    data from before this change) is excluded** — no timestamp means no ordering claim.
    The plain `progress_` prefix also matches `progress_at_` keys, which is why the scan
    keys off `progress_at_` and not `progress_`.
  - ended entries: keys starting `"ended_pending_"` → strip prefix.
- Keep the existing style: fire-and-forget writes via `scope.launch { runCatching { … } }`,
  reads via `runCatching { … }.getOrNull()` semantics where present today.

### 2c. Call-site updates

`main/kotlin/cast/android/service/PlaybackService.kt`:
- `startProgressSync` ticker (~line 457): 
  `progressStore.cacheProgress(episodeId, progressMs, System.currentTimeMillis())` and
  the WS message becomes
  `"""{"type":"update","episodeId":"$episodeId","progressMs":$progressMs,"updatedAt":${System.currentTimeMillis()}}"""`
  (hoist one `val now = System.currentTimeMillis()` and use it for both).
- `sendWs` now returns the `Boolean` from the client (`private fun sendWs(...): Boolean`).

`main/kotlin/cast/android/service/QueuePlaybackListener.kt`:
- The `sendWs` constructor parameter becomes
  `(message: String, coalesceKey: String?) -> Boolean`.
- Pause flush in `onPlayWhenReadyChanged` (else-branch): same timestamp treatment —
  one `val now = System.currentTimeMillis()`, pass to `store.cacheProgress(episodeId,
  progressMs, now)` and include `"updatedAt":$now` in the `update` message.
- Both `ended` send sites — `onMediaItemTransition` (REASON_AUTO branch) and
  `onPlaybackStateChanged` (STATE_ENDED) — become: if the send did **not** go out live,
  persist the intent:

```kotlin
if (!sendWs("""{"type":"ended","episodeId":"$finishedId"}""", null))
    store.markEndedPending(finishedId)
store.clearCachedProgress(finishedId)
```

Why mark-on-failure only (not mark-always + clear-on-ack — there are no acks): if the
flag stayed set after a successful live send, every later reconnect would replay `ended`
and silently re-mark an episode the user meanwhile marked unplayed in the webapp.

### 2d. Update test fakes

The fake `PlaybackProgressStore` implementations in
`test/kotlin/cast/android/service/QueuePlaybackListenerTest.kt` and
`test/kotlin/cast/android/service/ResumeSeekTest.kt` (and anywhere else the compiler
complains) implement the new members trivially (in-memory map + sets). Existing test
assertions must keep passing unchanged — this milestone alters no observable behavior
while online except the extra `updatedAt` field in outgoing JSON (fix any test that
string-matches the `update` message).

### Verify & commit

Compile/test via CI (push) or on the Mac: `cd android && ./gradlew test`.
Commit: `Persist listen timestamps and pending mark-played locally`
(may be folded into milestone 3's commit if preferred — 2 alone has no user-visible effect).

---

## Milestone 3 — Android: flush the outbox on every socket open

### 3a. `PlaybackWebSocketClient`: expose an "opened" signal

```kotlin
private val _opened = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
val opened: SharedFlow<Unit> = _opened.asSharedFlow()
```

In `onOpen`, after flushing the in-memory `pending` queue: `_opened.tryEmit(Unit)`.

### 3b. New class `main/kotlin/cast/android/service/ProgressOutboxFlusher.kt`

Plain class, no Android dependencies (that keeps its test a plain JVM test):

```kotlin
class ProgressOutboxFlusher(
    private val store: PlaybackProgressStore,
    private val sendWs: (message: String, coalesceKey: String?) -> Boolean,
) {
    suspend fun flush() {
        val pending = store.pendingSync()
        for (p in pending.progress) {
            sendWs(
                """{"type":"update","episodeId":"${p.episodeId}","progressMs":${p.progressMs},"updatedAt":${p.atMillis}}""",
                p.episodeId,
            )
        }
        for (episodeId in pending.endedEpisodeIds) {
            if (sendWs("""{"type":"ended","episodeId":"$episodeId"}""", null))
                store.clearEndedPending(episodeId)
        }
    }
}
```

Notes:
- Progress entries are **not** cleared after flushing — they still serve the head-start
  resume seek, and re-sending them later is harmless (server discards stale timestamps).
- Updates go before endeds so a finished episode ends up `played` regardless of both
  being pending.
- If a send fails mid-flush (socket died again), nothing is lost: ended flags stay set,
  progress entries stay; the next `opened` emission retries.

### 3c. Wire up in `PlaybackService.onCreate`

Next to the existing `states` collector:

```kotlin
val flusher = ProgressOutboxFlusher(progressStore, ::sendWs)
serviceScope.launch {
    playbackWebSocketClient.opened.collect { flusher.flush() }
}
```

Ordering: create `progressStore` before `playbackWebSocketClient.connect()` is called
(it already is, line ~103 vs ~143) and launch this collector before `connect()` too, so
the very first socket open also flushes — move `playbackWebSocketClient.connect()` below
the collector launches if needed. `SharedFlow` has no replay here; a flush missed before
the collector starts is only recovered on the *next* reconnect, so the order matters.

### 3d. Test — `test/kotlin/cast/android/service/ProgressOutboxFlusherTest.kt`

Plain Kotest/JUnit (match whichever style neighboring non-Robolectric tests use), fake
store + a recording `sendWs` lambda:

- sends one timestamped `update` per pending progress entry, coalesce key = episode id;
- sends `ended` for each pending-ended id and clears the flag when send returns true;
- keeps the ended flag when send returns false;
- sends nothing when the store has nothing pending.

The "entries without timestamps are skipped" case belongs to the store, not the flusher —
cover it in a small `DataStorePlaybackProgressStore` test only if one already exists;
otherwise the store fake in tests makes it moot and the real-store behavior is covered by
the prefix-scan keying off `progress_at_` (code review point, not worth a Robolectric
DataStore harness).

### Verify & commit

CI (push) or Mac: `cd android && ./gradlew test`.
Commit: `Flush offline progress and mark-played to the server on reconnect`

---

## Milestone 4 — manual end-to-end verification (on the phone)

Device access: adb over Tailscale (see memory note `adb-phone-over-tailscale`).
App id: `cast.android`. Server runs on the VPS; webapp shows playback state.

Scenario A — offline listen, process survives:
1. Download an episode; start playing it online; note webapp progress updates.
2. Airplane mode on: `adb shell cmd connectivity airplane-mode enable`.
3. Listen ~2 min. Webapp progress is frozen (expected).
4. Airplane mode off. Within a few seconds of the socket reconnecting, webapp progress
   jumps to the phone's position **without pausing/resuming playback**.

Scenario B — offline listen, process killed (the case this work exists for):
1. Airplane mode on; play a downloaded episode ~2 min; note the position.
2. `adb shell am force-stop cast.android`.
3. Airplane mode off; reopen the app; start any playback (service must start).
4. Webapp shows the offline position (or further) for that episode — previously it
   showed the last online position.
5. Variant: let the episode *finish* while offline before force-stop → after step 3 the
   episode shows as played in the webapp.

Scenario C — stale device does not clobber:
1. Phone offline mid-episode at position P1.
2. In the webapp, play the same episode past P1 to P2 (or use it on another episode and
   just advance this one by seeking + pausing so the server stores newer progress).
3. Phone back online → server keeps P2 (webapp does not rewind to P1). Note: the phone
   *UI* may still show P1 locally until its next `get` — only the server/webapp value is
   asserted here.

## After landing

Update memory `project_android_offline_outbox`: milestones done, what remains deferred
(queue-edit outbox, WorkManager flush-while-app-closed, webapp timestamps).

Known accepted limitations (record, don't fix now):
- Flush requires the playback service to be running; progress recorded offline reaches
  the server on next app/playback start, not the moment connectivity returns.
- An `ended` accepted by a live socket that dies before TCP delivery is lost (no acks);
  the ping-based half-open detection makes this window small.
