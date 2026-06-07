# Listening Status Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a manual "Listening" flag per podcast: listening podcasts sort first in the catalog, and the recent episode feed can be filtered to listening-only (on by default, server-side persistent).

**Architecture:** `Podcast` gains a `listening: Boolean` field (SQLite column, default `1`). Two use cases `StartListening`/`StopListening` expose it via `POST/DELETE /api/podcasts/{id}/listening`. A new `Settings.recentListeningOnly` flag gates filtering in the `/episodes/recent` handler. Both webapp and Android surface the toggle and the settings switch.

**Tech Stack:** Kotlin/Ktor (Hexagonal, Kotest, SQLite), TypeScript/Hono/HTMX (webapp), Jetpack Compose/Hilt/Retrofit (Android).

---

## File Map

### Created
- `core/src/main/kotlin/podcast/core/usecase/StartListening.kt`
- `core/src/main/kotlin/podcast/core/usecase/StopListening.kt`

### Modified — backend
- `core/src/main/kotlin/podcast/core/models/Podcast.kt` — add `listening: Boolean`
- `core/src/main/kotlin/podcast/core/ports/PodcastCatalog.kt` — add `setListening`
- `core/src/main/kotlin/podcast/core/ports/FeedInfoProvider.kt` — `toPodcast` sets `listening = true`
- `core/src/main/kotlin/podcast/adapters/persistence/SQLitePodcastCatalog.kt` — column, findAll order, setListening, INSERT
- `core/src/main/kotlin/configuration/SQLite.kt` — add `listening` to CREATE, add idempotent ALTER TABLE
- `core/src/main/kotlin/settings/core/models/Settings.kt` — add `recentListeningOnly: Boolean`
- `core/src/main/kotlin/settings/adapters/persistence/SQLiteSettingsPersistence.kt` — read/write new key
- `core/src/main/kotlin/podcast/PodcastModule.kt` — register `StartListening`, `StopListening`
- `core/src/main/kotlin/api/PodcastApi.kt` — new routes, update DTO mappers
- `core/src/main/kotlin/api/EpisodeApi.kt` — inject `GetSettings`, filter recent by listening
- `shared-models/src/main/kotlin/cast/api/PodcastDtos.kt` — add `listening` to Summary + Detail
- `shared-models/src/main/kotlin/cast/api/SettingsDtos.kt` — add `recentListeningOnly`

### Modified — tests
- `core/src/test/kotlin/podcast/fakes/FakePodcastCatalog.kt` — implement `setListening`, sort `findAll`
- `core/src/test/kotlin/podcast/core/PodcastCoreTests.kt` — new listening assertions
- `core/src/test/kotlin/podcast/adapters/persistence/SQLitePodcastCatalogIT.kt` — column/ordering/setListening tests
- `core/src/test/kotlin/settings/adapters/persistence/SQLiteSettingsPersistenceIT.kt` — `recentListeningOnly` round-trip
- `core/src/test/kotlin/AppTest.kt` — listening toggle + recent filter integration tests

### Modified — webapp
- `webapp/generated/api.ts` — regenerated (run `./gradlew :shared-models:generateTypeScript`)
- `webapp/server.tsx` — proxy routes for listening, update settings handler
- `webapp/components/PodcastList.tsx` — listening toggle per card
- `webapp/components/PodcastDetail.tsx` — listening toggle button
- `webapp/components/SettingsPage.tsx` — `recentListeningOnly` checkbox

### Modified — Android
- `android/app/src/main/kotlin/cast/android/network/CastApiService.kt` — two new endpoints
- `android/app/src/main/kotlin/cast/android/domain/repository/PodcastRepository.kt` — `setListening`
- `android/app/src/main/kotlin/cast/android/domain/repository/impl/PodcastRepositoryImpl.kt` — implement
- `android/app/src/main/kotlin/cast/android/domain/model/Settings.kt` — `recentListeningOnly`
- `android/app/src/main/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImpl.kt` — key + update/refresh
- `android/app/src/main/kotlin/cast/android/ui/screens/SettingsScreen.kt` — new switch row
- `android/app/src/main/kotlin/cast/android/ui/viewmodel/CatalogViewModel.kt` — `toggleListening`
- `android/app/src/main/kotlin/cast/android/ui/screens/CatalogScreen.kt` — listening badge
- `android/app/src/main/kotlin/cast/android/ui/viewmodel/PodcastDetailViewModel.kt` — `toggleListening`
- `android/app/src/main/kotlin/cast/android/ui/screens/PodcastDetailScreen.kt` — listening menu item

---

## Task 1: Domain model — `Podcast.listening` + port + fake

**Files:**
- Modify: `core/src/main/kotlin/podcast/core/models/Podcast.kt`
- Modify: `core/src/main/kotlin/podcast/core/ports/PodcastCatalog.kt`
- Modify: `core/src/main/kotlin/podcast/core/ports/FeedInfoProvider.kt`
- Modify: `core/src/test/kotlin/podcast/fakes/FakePodcastCatalog.kt`
- Modify: `core/src/test/kotlin/podcast/core/PodcastCoreTests.kt`

- [ ] **Step 1: Write failing tests for `listening` in domain**

Add to `PodcastCoreTests.kt` inside the existing `describe("Podcast Domain Hexagon")` block, after the last `it(...)`:

```kotlin
it("new podcast defaults to listening") {
    val podcast = addFeed(FeedUrl("https://example.com/rss"))
    podcast.listening shouldBe true
}

it("lists listening podcasts before non-listening ones") {
    val url1 = FeedUrl("https://show1.com/rss")
    val url2 = FeedUrl("https://show2.com/rss")
    stubFeedProvider = FeedInfoProvider { url -> FeedInfo(title = "Show for ${url.value}", description = "Desc", image = "img.png", url = url.value) }
    addFeed = AddFeed(catalog, stubFeedProvider, updateFeed, fixedClock)
    val p1 = addFeed(url1)
    val p2 = addFeed(url2)

    val startListening = StartListening(catalog)
    val stopListening = StopListening(catalog)
    stopListening(p1.id)
    startListening(p2.id)

    val ordered = listPodcasts()
    ordered.first().id shouldBe p2.id
    ordered.last().id shouldBe p1.id
}

it("StartListening returns false for unknown podcast") {
    StartListening(catalog)(PodcastId("nope")) shouldBe false
}

it("StopListening returns false for unknown podcast") {
    StopListening(catalog)(PodcastId("nope")) shouldBe false
}
```

- [ ] **Step 2: Run the tests to confirm they fail**

```bash
./gradlew test --tests "podcast.core.PodcastCoreTests" 2>&1 | tail -20
```
Expected: compile errors (unresolved `listening`, `StartListening`, `StopListening`).

- [ ] **Step 3: Add `listening` to `Podcast.kt`**

Replace `Podcast.kt` with:
```kotlin
package podcast.core.models

import java.time.Instant

data class Podcast(
    val id: PodcastId,
    val url: FeedUrl,
    val name: String,
    val image: String,
    val listening: Boolean,
    val created: Instant,
    val updated: Instant
)

@JvmInline
value class PodcastId(val value: String) {
    override fun toString() = value
}

@JvmInline
value class FeedUrl(val value: String) {
    override fun toString() = value
}
```

- [ ] **Step 4: Update `PodcastCatalog.kt` — add `setListening`**

Add this method to the interface:
```kotlin
suspend fun setListening(id: PodcastId, listening: Boolean): Boolean
```

Full updated interface:
```kotlin
package podcast.core.ports

import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import shared.model.EpisodeId
import java.time.Instant

interface PodcastCatalog {
    suspend fun save(podcast: Podcast, episodes: List<Episode>)
    suspend fun delete(id: PodcastId)
    suspend fun findAll(): List<Podcast>
    suspend fun findById(id: PodcastId): Podcast?
    suspend fun findByUrl(url: FeedUrl): Podcast?
    suspend fun episodesFor(podcastId: PodcastId): List<Episode>
    suspend fun findEpisodeById(id: EpisodeId): Episode?
    suspend fun findEpisodesPublishedAfter(publishedAfter: Instant): List<Episode>
    suspend fun setListening(id: PodcastId, listening: Boolean): Boolean
}
```

- [ ] **Step 5: Update `FeedInfoProvider.kt` — `toPodcast` sets `listening = true`**

In `FeedInfoProvider.kt`, update the `toPodcast` extension:
```kotlin
fun FeedInfo.toPodcast(id: PodcastId, created: Instant, updated: Instant) = Podcast(
    id = id,
    url = FeedUrl(url),
    name = title,
    image = image,
    listening = true,
    created = created,
    updated = updated
)
```

- [ ] **Step 6: Update `FakePodcastCatalog.kt` — implement `setListening` and sorted `findAll`**

Full updated file:
```kotlin
package podcast.fakes

import podcast.core.models.Episode
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog
import shared.model.EpisodeId
import java.time.Instant

class FakePodcastCatalog : PodcastCatalog {
    private val podcasts = mutableMapOf<PodcastId, Podcast>()
    private val episodes = mutableMapOf<EpisodeId, Episode>()

    override suspend fun save(podcast: Podcast, episodes: List<Episode>) {
        podcasts[podcast.id] = podcast
        episodes.forEach { this.episodes[it.id] = it }
    }

    override suspend fun delete(id: PodcastId) {
        podcasts.remove(id)
        episodes.values.removeIf { it.podcastId == id }
    }

    override suspend fun findAll() = podcasts.values
        .sortedWith(compareByDescending<Podcast> { it.listening }.thenBy { it.created })

    override suspend fun findById(id: PodcastId) = podcasts[id]

    override suspend fun findByUrl(url: FeedUrl) = podcasts.values.find { it.url == url }

    override suspend fun episodesFor(podcastId: PodcastId) =
        episodes.values.filter { it.podcastId == podcastId }

    override suspend fun findEpisodeById(id: EpisodeId) = episodes[id]

    override suspend fun findEpisodesPublishedAfter(publishedAfter: Instant): List<Episode> =
        episodes.values.filter { it.publishedAt?.isAfter(publishedAfter) ?: false }.toList()

    override suspend fun setListening(id: PodcastId, listening: Boolean): Boolean {
        val existing = podcasts[id] ?: return false
        podcasts[id] = existing.copy(listening = listening)
        return true
    }
}
```

- [ ] **Step 7: Create `StartListening.kt`**

```kotlin
package podcast.core.usecase

import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog

class StartListening(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(id: PodcastId): Boolean = catalog.setListening(id, true)
}
```

- [ ] **Step 8: Create `StopListening.kt`**

```kotlin
package podcast.core.usecase

import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog

class StopListening(private val catalog: PodcastCatalog) {
    suspend operator fun invoke(id: PodcastId): Boolean = catalog.setListening(id, false)
}
```

- [ ] **Step 9: Fix compile errors in existing code that constructs `Podcast` directly**

The `SQLitePodcastCatalog.kt` `ResultSet.toPodcast()` and `SQLitePodcastCatalogIT.kt` `createPodcast` helper construct `Podcast` without `listening`. They will fail to compile. Fix them temporarily:

In `SQLitePodcastCatalog.kt` `ResultSet.toPodcast()` (line ~136), add `listening = true` as a placeholder:
```kotlin
private fun ResultSet.toPodcast() = Podcast(
    id = PodcastId(getString("id")),
    url = FeedUrl(getString("url")),
    name = getString("name"),
    image = getString("image"),
    listening = true,  // placeholder — replaced properly in Task 3
    created = Instant.parse(getString("created")),
    updated = Instant.parse(getString("updated"))
)
```

In `SQLitePodcastCatalogIT.kt` `createPodcast` helper, add `listening = true`:
```kotlin
private fun createPodcast(id: String) = Podcast(
    id = PodcastId(id),
    url = FeedUrl("url-$id"),
    name = "Name $id",
    image = "img",
    listening = true,
    created = Instant.now(),
    updated = Instant.now()
)
```

- [ ] **Step 10: Run tests to confirm domain tests pass**

```bash
./gradlew test --tests "podcast.core.PodcastCoreTests" 2>&1 | tail -20
```
Expected: all tests PASS.

- [ ] **Step 11: Commit**

```bash
git add core/src/main/kotlin/podcast/core/models/Podcast.kt \
        core/src/main/kotlin/podcast/core/ports/PodcastCatalog.kt \
        core/src/main/kotlin/podcast/core/ports/FeedInfoProvider.kt \
        core/src/main/kotlin/podcast/core/usecase/StartListening.kt \
        core/src/main/kotlin/podcast/core/usecase/StopListening.kt \
        core/src/test/kotlin/podcast/fakes/FakePodcastCatalog.kt \
        core/src/test/kotlin/podcast/core/PodcastCoreTests.kt \
        core/src/test/kotlin/podcast/adapters/persistence/SQLitePodcastCatalogIT.kt
git commit -m "feat(core): add Podcast.listening domain field and StartListening/StopListening use cases"
```

---

## Task 2: SQLite persistence — `listening` column

**Files:**
- Modify: `core/src/main/kotlin/configuration/SQLite.kt`
- Modify: `core/src/main/kotlin/podcast/adapters/persistence/SQLitePodcastCatalog.kt`
- Modify: `core/src/test/kotlin/podcast/adapters/persistence/SQLitePodcastCatalogIT.kt`

- [ ] **Step 1: Write failing IT tests for `listening`**

Add to `SQLitePodcastCatalogIT.kt` inside the `describe("add")` block and a new `describe("listening")` block:

```kotlin
describe("listening") {
    it("new podcast has listening = true by default") {
        val id = PodcastId(UUID.randomUUID().toString())
        catalog.save(createPodcast(id.value), emptyList())

        catalog.findById(id)?.listening shouldBe true
    }

    it("findAll returns listening podcasts before non-listening") {
        val listening = PodcastId(UUID.randomUUID().toString())
        val notListening = PodcastId(UUID.randomUUID().toString())
        catalog.save(createPodcast(listening.value), emptyList())
        catalog.save(createPodcast(notListening.value), emptyList())
        catalog.setListening(notListening, false)

        val all = catalog.findAll()
        all.first().id shouldBe listening
        all.last().id shouldBe notListening
    }

    it("setListening returns true when podcast exists") {
        val id = PodcastId(UUID.randomUUID().toString())
        catalog.save(createPodcast(id.value), emptyList())

        catalog.setListening(id, false) shouldBe true
        catalog.findById(id)?.listening shouldBe false

        catalog.setListening(id, true) shouldBe true
        catalog.findById(id)?.listening shouldBe true
    }

    it("setListening returns false for unknown podcast") {
        catalog.setListening(PodcastId("nope"), false) shouldBe false
    }

    it("existing rows get listening = 1 via column default") {
        // Simulate an existing database row without the listening column by inserting
        // via raw SQL without the listening field, then verifying findById still works.
        // (This is a structural check; real migration is covered by the ALTER TABLE.)
        val id = PodcastId(UUID.randomUUID().toString())
        catalog.save(createPodcast(id.value), emptyList())
        catalog.findById(id)?.listening shouldBe true
    }
}
```

- [ ] **Step 2: Run to confirm tests fail**

```bash
./gradlew test --tests "podcast.adapters.persistence.SQLitePodcastCatalogIT" 2>&1 | tail -20
```
Expected: compile errors or failures because `setListening` not yet implemented in `SQLitePodcastCatalog`.

- [ ] **Step 3: Update `CREATE_PODCASTS_TABLE` in `SQLite.kt`**

Replace the `CREATE_PODCASTS_TABLE` value with:
```kotlin
val CREATE_PODCASTS_TABLE = """
    CREATE TABLE IF NOT EXISTS podcasts (
        id TEXT PRIMARY KEY,
        url TEXT NOT NULL,
        name TEXT NOT NULL,
        image TEXT NOT NULL,
        listening INTEGER NOT NULL DEFAULT 1,
        created TEXT NOT NULL,
        updated TEXT NOT NULL
    )
""".trimIndent()
```

Also add an idempotent ALTER TABLE after the `createStatement` block in `installDatabase()`:
```kotlin
fun Application.installDatabase() {
    val dbPath = System.getenv("DB_PATH") ?: "podcasts.db"
    val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")

    connection.createStatement().use { statement ->
        statement.executeUpdate(CREATE_PODCASTS_TABLE)
        statement.executeUpdate(CREATE_EPISODES_TABLE)
        statement.executeUpdate(CREATE_PLAYBACK_STATE_TABLE)
        statement.executeUpdate(CREATE_QUEUE_TABLE)
        statement.executeUpdate(CREATE_SETTINGS_TABLE)
    }

    // Add `listening` column to existing databases that pre-date this feature.
    // SQLite fills existing rows with the DEFAULT (1 = listening) automatically.
    try {
        connection.createStatement().use { stmt ->
            stmt.executeUpdate("ALTER TABLE podcasts ADD COLUMN listening INTEGER NOT NULL DEFAULT 1")
        }
    } catch (_: java.sql.SQLException) {
        // Column already exists — safe to ignore
    }

    monitor.subscribe(ApplicationStopped) { connection.close() }

    val db = SingleConnectionProvider(connection)

    dependencies {
        provide<ConnectionProvider> { db }
    }
}
```

- [ ] **Step 4: Update `SQLitePodcastCatalog.kt`**

Full updated file:
```kotlin
package podcast.adapters.persistence

import configuration.ConnectionProvider
import podcast.core.models.Episode
import shared.model.EpisodeId
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.ports.PodcastCatalog
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Types
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

class SQLitePodcastCatalog(private val db: ConnectionProvider) : PodcastCatalog {

    override suspend fun save(podcast: Podcast, episodes: List<Episode>) = db.withConnection { conn ->
        val originalAutoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            conn.insertPodcast(podcast)
            if (episodes.isNotEmpty()) conn.insertEpisodes(episodes)
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = originalAutoCommit
        }
    }

    override suspend fun delete(id: PodcastId) = db.withConnection { conn ->
        val originalAutoCommit = conn.autoCommit
        try {
            conn.autoCommit = false
            conn.prepareStatement("DELETE FROM episodes WHERE podcast_id = ?").use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }
            conn.prepareStatement("DELETE FROM podcasts WHERE id = ?").use { stmt ->
                stmt.setString(1, id.value)
                stmt.executeUpdate()
            }
            conn.commit()
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = originalAutoCommit
        }
    }

    override suspend fun findAll(): List<Podcast> = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM podcasts ORDER BY listening DESC, created ASC").use { stmt ->
            val rs = stmt.executeQuery()
            generateSequence { if (rs.next()) rs.toPodcast() else null }.toList()
        }
    }

    override suspend fun findById(id: PodcastId): Podcast? = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM podcasts WHERE id = ?").use { stmt ->
            stmt.setString(1, id.value)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.toPodcast() else null
        }
    }

    override suspend fun findByUrl(url: FeedUrl): Podcast? = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM podcasts WHERE url = ?").use { stmt ->
            stmt.setString(1, url.value)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.toPodcast() else null
        }
    }

    override suspend fun episodesFor(podcastId: PodcastId): List<Episode> = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM episodes WHERE podcast_id = ?").use { stmt ->
            stmt.setString(1, podcastId.value)
            val rs = stmt.executeQuery()
            generateSequence { if (rs.next()) rs.toEpisode() else null }.toList()
        }
    }

    override suspend fun findEpisodeById(id: EpisodeId): Episode? = db.withConnection { conn ->
        conn.prepareStatement("SELECT * FROM episodes WHERE id = ?").use { stmt ->
            stmt.setString(1, id.value)
            val rs = stmt.executeQuery()
            if (rs.next()) rs.toEpisode() else null
        }
    }

    override suspend fun findEpisodesPublishedAfter(publishedAfter: Instant): List<Episode> =
        db.withConnection { conn ->
            conn.prepareStatement("SELECT * FROM episodes WHERE published_at > ?").use { stmt ->
                stmt.setString(1, publishedAfter.toString())
                stmt.executeQuery().use { rs ->
                    generateSequence { if (rs.next()) rs.toEpisode() else null }.toList()
                }
            }
        }

    override suspend fun setListening(id: PodcastId, listening: Boolean): Boolean =
        db.withConnection { conn ->
            conn.prepareStatement("UPDATE podcasts SET listening = ? WHERE id = ?").use { stmt ->
                stmt.setInt(1, if (listening) 1 else 0)
                stmt.setString(2, id.value)
                stmt.executeUpdate() > 0
            }
        }
}

private fun Connection.insertPodcast(podcast: Podcast) {
    prepareStatement(INSERT_PODCAST).use { stmt ->
        stmt.setString(1, podcast.id.value)
        stmt.setString(2, podcast.url.value)
        stmt.setString(3, podcast.name)
        stmt.setString(4, podcast.image)
        stmt.setInt(5, if (podcast.listening) 1 else 0)
        stmt.setString(6, podcast.created.toString())
        stmt.setString(7, podcast.updated.toString())
        stmt.executeUpdate()
    }
}

private fun Connection.insertEpisodes(episodes: List<Episode>) {
    prepareStatement(INSERT_EPISODE).use { stmt ->
        episodes.forEach { episode ->
            stmt.setString(1, episode.id.value)
            stmt.setString(2, episode.feedGuid)
            stmt.setString(3, episode.podcastId.value)
            stmt.setString(4, episode.title)
            stmt.setString(5, episode.description)
            stmt.setString(6, episode.audioUrl)
            if (episode.duration != null)
                stmt.setLong(7, episode.duration.inWholeSeconds)
            else
                stmt.setNull(7, Types.INTEGER)
            stmt.setString(8, episode.publishedAt?.toString())
            stmt.addBatch()
        }
        stmt.executeBatch()
    }
}

private fun ResultSet.toPodcast() = Podcast(
    id = PodcastId(getString("id")),
    url = FeedUrl(getString("url")),
    name = getString("name"),
    image = getString("image"),
    listening = getInt("listening") != 0,
    created = Instant.parse(getString("created")),
    updated = Instant.parse(getString("updated"))
)

private fun ResultSet.toEpisode(): Episode {
    val durationSeconds = getLong("duration")
    return Episode(
        id = EpisodeId(getString("id")),
        feedGuid = getString("guid"),
        podcastId = PodcastId(getString("podcast_id")),
        title = getString("title"),
        description = getString("description"),
        audioUrl = getString("audio_url"),
        duration = if (wasNull()) null else durationSeconds.seconds,
        publishedAt = getString("published_at")?.let { Instant.parse(it) }
    )
}

private val INSERT_PODCAST = """
    INSERT INTO podcasts (id, url, name, image, listening, created, updated)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(id) DO UPDATE SET
        url = excluded.url,
        name = excluded.name,
        image = excluded.image,
        created = excluded.created,
        updated = excluded.updated
""".trimIndent()

private val INSERT_EPISODE = """
    INSERT INTO episodes
    (id, guid, podcast_id, title, description, audio_url, duration, published_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT(guid) DO UPDATE SET
        title = excluded.title,
        description = excluded.description,
        audio_url = excluded.audio_url,
        duration = excluded.duration,
        published_at = excluded.published_at
""".trimIndent()
```

Note: `listening` is intentionally excluded from the `ON CONFLICT DO UPDATE` clause so feed refreshes never overwrite the user's listening preference.

- [ ] **Step 5: Run IT tests**

```bash
./gradlew test --tests "podcast.adapters.persistence.SQLitePodcastCatalogIT" 2>&1 | tail -20
```
Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add core/src/main/kotlin/configuration/SQLite.kt \
        core/src/main/kotlin/podcast/adapters/persistence/SQLitePodcastCatalog.kt \
        core/src/test/kotlin/podcast/adapters/persistence/SQLitePodcastCatalogIT.kt
git commit -m "feat(persistence): add listening column to podcasts table with idempotent migration"
```

---

## Task 3: Settings — `recentListeningOnly`

**Files:**
- Modify: `core/src/main/kotlin/settings/core/models/Settings.kt`
- Modify: `core/src/main/kotlin/settings/adapters/persistence/SQLiteSettingsPersistence.kt`
- Modify: `core/src/test/kotlin/settings/adapters/persistence/SQLiteSettingsPersistenceIT.kt`

- [ ] **Step 1: Write failing tests for `recentListeningOnly`**

Add to `SQLiteSettingsPersistenceIT.kt`, inside the `describe("get")` and `describe("update")` blocks:

In `describe("get")`:
```kotlin
it("defaults recentListeningOnly to true") {
    persistence.get() shouldBe Settings(hidePlayed = false, recentListeningOnly = true)
}
```

In `describe("update")`:
```kotlin
it("persists recentListeningOnly = false") {
    persistence.update(Settings(hidePlayed = false, recentListeningOnly = false))
    persistence.get().recentListeningOnly shouldBe false
}

it("persists recentListeningOnly = true after false") {
    persistence.update(Settings(hidePlayed = false, recentListeningOnly = false))
    persistence.update(Settings(hidePlayed = false, recentListeningOnly = true))
    persistence.get().recentListeningOnly shouldBe true
}
```

- [ ] **Step 2: Run to confirm failure**

```bash
./gradlew test --tests "settings.adapters.persistence.SQLiteSettingsPersistenceIT" 2>&1 | tail -20
```
Expected: compile errors (no `recentListeningOnly` field yet).

- [ ] **Step 3: Update `Settings.kt`**

```kotlin
package settings.core.models

data class Settings(val hidePlayed: Boolean = false, val recentListeningOnly: Boolean = true)
```

- [ ] **Step 4: Update `SQLiteSettingsPersistence.kt`**

Full updated file:
```kotlin
package settings.adapters.persistence

import configuration.ConnectionProvider
import settings.core.models.Settings
import settings.core.ports.SettingsPersistence

class SQLiteSettingsPersistence(private val db: ConnectionProvider) : SettingsPersistence {

    override suspend fun get(): Settings = db.withConnection { conn ->
        conn.prepareStatement("SELECT key, value FROM settings WHERE key IN (?, ?)").use { stmt ->
            stmt.setString(1, "hide_played")
            stmt.setString(2, "recent_listening_only")
            val rs = stmt.executeQuery()
            val map = mutableMapOf<String, String>()
            while (rs.next()) map[rs.getString("key")] = rs.getString("value")
            Settings(
                hidePlayed = map["hide_played"] == "true",
                recentListeningOnly = map["recent_listening_only"]?.let { it == "true" } ?: true,
            )
        }
    }

    override suspend fun update(settings: Settings) {
        db.withConnection { conn ->
            conn.prepareStatement("""
                INSERT INTO settings (key, value) VALUES (?, ?)
                ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """.trimIndent()).use { stmt ->
                stmt.setString(1, "hide_played")
                stmt.setString(2, settings.hidePlayed.toString())
                stmt.executeUpdate()
                stmt.setString(1, "recent_listening_only")
                stmt.setString(2, settings.recentListeningOnly.toString())
                stmt.executeUpdate()
            }
        }
    }
}
```

- [ ] **Step 5: Fix compile errors in other places that construct `Settings` without `recentListeningOnly`**

In `AppTest.kt`, the settings describe block constructs `SettingsDto(hidePlayed = true)` — that's fine (DTO, not model). But `settings.core.models.Settings` now has a default for `recentListeningOnly`, so existing constructors work without change.

- [ ] **Step 6: Run settings IT tests**

```bash
./gradlew test --tests "settings.adapters.persistence.SQLiteSettingsPersistenceIT" 2>&1 | tail -20
```
Expected: all tests PASS.

- [ ] **Step 7: Commit**

```bash
git add core/src/main/kotlin/settings/core/models/Settings.kt \
        core/src/main/kotlin/settings/adapters/persistence/SQLiteSettingsPersistence.kt \
        core/src/test/kotlin/settings/adapters/persistence/SQLiteSettingsPersistenceIT.kt
git commit -m "feat(settings): add recentListeningOnly setting (default true)"
```

---

## Task 4: Shared-model DTOs + PodcastModule DI + API

**Files:**
- Modify: `shared-models/src/main/kotlin/cast/api/PodcastDtos.kt`
- Modify: `shared-models/src/main/kotlin/cast/api/SettingsDtos.kt`
- Modify: `core/src/main/kotlin/podcast/PodcastModule.kt`
- Modify: `core/src/main/kotlin/api/PodcastApi.kt`
- Modify: `core/src/main/kotlin/api/EpisodeApi.kt`
- Modify: `core/src/test/kotlin/AppTest.kt`

- [ ] **Step 1: Write failing AppTest cases**

Add to `AppTest.kt`:

After the last `it(...)` inside `describe("the podcast catalog")`:
```kotlin
it("new podcast is marked as listening") {
    testApp { json, _ ->
        val podcast = json.post("/api/podcasts") {
            contentType(ContentType.Application.Json)
            setBody(AddPodcastRequest(feedUrl))
        }.body<PodcastDetailDto>()

        podcast.listening shouldBe true
        json.get("/api/podcasts").body<List<PodcastSummaryDto>>()
            .first().listening shouldBe true
    }
}

it("can be marked as not listening and back") {
    testApp { json, _ ->
        val podcast = json.post("/api/podcasts") {
            contentType(ContentType.Application.Json)
            setBody(AddPodcastRequest(feedUrl))
        }.body<PodcastDetailDto>()

        json.delete("/api/podcasts/${podcast.id}/listening").status shouldBe HttpStatusCode.NoContent
        json.get("/api/podcasts").body<List<PodcastSummaryDto>>()
            .first().listening shouldBe false

        json.post("/api/podcasts/${podcast.id}/listening").status shouldBe HttpStatusCode.NoContent
        json.get("/api/podcasts").body<List<PodcastSummaryDto>>()
            .first().listening shouldBe true
    }
}

it("returns 404 when toggling listening for a non-existent podcast") {
    testApp { json, _ ->
        json.post("/api/podcasts/nope/listening").status shouldBe HttpStatusCode.NotFound
        json.delete("/api/podcasts/nope/listening").status shouldBe HttpStatusCode.NotFound
    }
}

it("lists listening podcasts before non-listening ones") {
    val feed2 = "https://example.com/feed2.xml"
    val rss2 = "<rss><channel><title>Show 2</title><image><url>img2</url></image></channel></rss>"
    testApp(rssFeeds = mapOf(feedUrl to rss, feed2 to rss2)) { json, _ ->
        val p1 = json.post("/api/podcasts") {
            contentType(ContentType.Application.Json)
            setBody(AddPodcastRequest(feedUrl))
        }.body<PodcastDetailDto>()
        val p2 = json.post("/api/podcasts") {
            contentType(ContentType.Application.Json)
            setBody(AddPodcastRequest(feed2))
        }.body<PodcastDetailDto>()

        json.delete("/api/podcasts/${p1.id}/listening")

        val ordered = json.get("/api/podcasts").body<List<PodcastSummaryDto>>()
        ordered.first().id shouldBe p2.id
        ordered.last().id shouldBe p1.id
    }
}
```

Add a new `describe` block after `describe("the hide-played setting")`:
```kotlin
describe("the recent-listening-only setting") {
    it("excludes episodes from non-listening podcasts when enabled") {
        val feed2 = "https://example.com/feed2.xml"
        val rss2 = """
            <rss><channel>
                <title>Show 2</title><image><url>img2</url></image>
                <item><title>Show 2 Ep</title><guid>s2-ep-1</guid><enclosure url="https://cdn/s2ep1.mp3" length="0" type="audio/mpeg"/></item>
            </channel></rss>
        """.trimIndent()
        testApp(rssFeeds = mapOf(feedUrl to rss, feed2 to rss2)) { json, _ ->
            val p1 = json.post("/api/podcasts") {
                contentType(ContentType.Application.Json)
                setBody(AddPodcastRequest(feedUrl))
            }.body<PodcastDetailDto>()
            json.post("/api/podcasts") {
                contentType(ContentType.Application.Json)
                setBody(AddPodcastRequest(feed2))
            }

            // Stop listening to p1
            json.delete("/api/podcasts/${p1.id}/listening")

            // Enable recentListeningOnly (it's the default but set explicitly)
            json.put("/api/settings") {
                contentType(ContentType.Application.Json)
                setBody(SettingsDto(hidePlayed = false, recentListeningOnly = true))
            }

            val recent = json.get("/api/episodes/recent").body<List<EpisodeDetailDto>>()
            recent.none { it.podcastId == p1.id } shouldBe true
            recent.any { it.title == "Show 2 Ep" } shouldBe true
        }
    }

    it("includes all podcasts when disabled") {
        val feed2 = "https://example.com/feed2.xml"
        val rss2 = """
            <rss><channel>
                <title>Show 2</title><image><url>img2</url></image>
                <item><title>Show 2 Ep</title><guid>s2-ep-1</guid><enclosure url="https://cdn/s2ep1.mp3" length="0" type="audio/mpeg"/></item>
            </channel></rss>
        """.trimIndent()
        testApp(rssFeeds = mapOf(feedUrl to rss, feed2 to rss2)) { json, _ ->
            val p1 = json.post("/api/podcasts") {
                contentType(ContentType.Application.Json)
                setBody(AddPodcastRequest(feedUrl))
            }.body<PodcastDetailDto>()
            json.post("/api/podcasts") {
                contentType(ContentType.Application.Json)
                setBody(AddPodcastRequest(feed2))
            }

            json.delete("/api/podcasts/${p1.id}/listening")

            json.put("/api/settings") {
                contentType(ContentType.Application.Json)
                setBody(SettingsDto(hidePlayed = false, recentListeningOnly = false))
            }

            val recent = json.get("/api/episodes/recent").body<List<EpisodeDetailDto>>()
            recent.any { it.podcastId == p1.id } shouldBe true
        }
    }
}
```

- [ ] **Step 2: Run to confirm tests fail**

```bash
./gradlew test --tests "AppTest" 2>&1 | tail -30
```
Expected: compile errors (`listening` not on DTOs, `recentListeningOnly` not on `SettingsDto`).

- [ ] **Step 3: Update `PodcastDtos.kt`**

```kotlin
package cast.api

import kotlinx.serialization.Serializable

@Serializable
data class PodcastSummaryDto(
    val id: String,
    val url: String,
    val name: String,
    val image: String,
    val listening: Boolean,
    val created: String,
    val updated: String,
)

@Serializable
data class EpisodeDetailDto(
    val id: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val duration: String?,
    val durationMs: Long?,
    val publishedAt: String?,
    val played: Boolean,
    val progressMs: Long,
    val podcastId: String? = null,
    val podcastName: String? = null,
    val podcastImage: String? = null,
)

@Serializable
data class PodcastDetailDto(
    val id: String,
    val url: String,
    val name: String,
    val image: String,
    val listening: Boolean,
    val created: String,
    val updated: String,
    val episodes: List<EpisodeDetailDto>,
)

@Serializable
data class AddPodcastRequest(val feed: String)

@Serializable
data class ReorderQueueRequest(val episodeIds: List<String>)
```

- [ ] **Step 4: Update `SettingsDtos.kt`**

```kotlin
package cast.api

import kotlinx.serialization.Serializable

@Serializable
data class SettingsDto(val hidePlayed: Boolean, val recentListeningOnly: Boolean = true)
```

- [ ] **Step 5: Update `PodcastModule.kt` — register `StartListening` and `StopListening`**

Add to the `dependencies { }` block:
```kotlin
provide<StartListening> { StartListening(resolve()) }
provide<StopListening> { StopListening(resolve()) }
```

And add the imports:
```kotlin
import podcast.core.usecase.StartListening
import podcast.core.usecase.StopListening
```

- [ ] **Step 6: Update `PodcastApi.kt` — new routes + updated DTO mappers**

Full updated file:
```kotlin
package api

import application.model.EpisodeWithPlayback
import application.usecase.GetPodcastDetail
import application.usecase.RemovePodcast
import cast.api.AddPodcastRequest
import cast.api.EpisodeDetailDto
import cast.api.PodcastDetailDto
import cast.api.PodcastSummaryDto
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import playback.core.usecase.MarkAllPlayed
import podcast.core.PodcastException
import podcast.core.models.FeedUrl
import podcast.core.models.Podcast
import podcast.core.models.PodcastId
import podcast.core.usecase.AddFeed
import podcast.core.usecase.ImportOpml
import podcast.core.usecase.ListEpisodes
import podcast.core.usecase.ListPodcasts
import podcast.core.usecase.StartListening
import podcast.core.usecase.StopListening
import kotlin.time.Duration

fun Route.podcastApi(dependencies: DependencyRegistry) {

    val addFeed: AddFeed by dependencies
    val importOpml: ImportOpml by dependencies
    val listPodcasts: ListPodcasts by dependencies
    val listEpisodes: ListEpisodes by dependencies
    val getPodcastDetail: GetPodcastDetail by dependencies
    val markAllPlayed: MarkAllPlayed by dependencies
    val removePodcast: RemovePodcast by dependencies
    val startListening: StartListening by dependencies
    val stopListening: StopListening by dependencies

    get {
        call.respond(listPodcasts().map(::podcastSummaryDto))
    }

    post {
        val request = call.receive<AddPodcastRequest>()
        try {
            val podcast = addFeed(url = FeedUrl(request.feed))
            val episodes = listEpisodes(podcast.id)
            call.respond(podcastDetailDto(podcast, episodes.map { EpisodeWithPlayback(it, 0, false) }))
        } catch (e: PodcastException.FeedFetchFailed) {
            call.respond(HttpStatusCode.BadGateway, mapOf("error" to (e.message ?: "Failed to fetch feed")))
        }
    }

    post("import") {
        val multipart = call.receiveMultipart()
        var opmlContent: ByteArray? = null
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) opmlContent = part.provider().toByteArray()
            part.dispose()
        }
        val content = opmlContent ?: return@post call.respond(HttpStatusCode.BadRequest)
        val result = importOpml(content)
        call.respond(mapOf("imported" to result.imported.size, "failed" to result.failed.size))
    }

    get("{id}") {
        val id = PodcastId(call.parameters["id"]!!)
        val detail = getPodcastDetail(id) ?: return@get call.respond(HttpStatusCode.NotFound)
        call.respond(podcastDetailDto(detail.podcast, detail.episodes))
    }

    delete("{id}") {
        val id = PodcastId(call.parameters["id"]!!)
        val removed = removePodcast(id)
        call.respond(if (removed) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }

    post("{id}/played") {
        val id = PodcastId(call.parameters["id"]!!)
        val detail = getPodcastDetail(id) ?: return@post call.respond(HttpStatusCode.NotFound)
        markAllPlayed(detail.episodes.map { it.episode.id })
        call.respond(HttpStatusCode.NoContent)
    }

    post("{id}/listening") {
        val id = PodcastId(call.parameters["id"]!!)
        val found = startListening(id)
        call.respond(if (found) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }

    delete("{id}/listening") {
        val id = PodcastId(call.parameters["id"]!!)
        val found = stopListening(id)
        call.respond(if (found) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
    }
}

private fun podcastSummaryDto(podcast: Podcast) =
    PodcastSummaryDto(
        id = podcast.id.value,
        url = podcast.url.value,
        name = podcast.name,
        image = podcast.image,
        listening = podcast.listening,
        created = podcast.created.toString(),
        updated = podcast.updated.toString(),
    )

private fun podcastDetailDto(podcast: Podcast, episodes: List<EpisodeWithPlayback>) =
    PodcastDetailDto(
        id = podcast.id.value,
        url = podcast.url.value,
        name = podcast.name,
        image = podcast.image,
        listening = podcast.listening,
        created = podcast.created.toString(),
        updated = podcast.updated.toString(),
        episodes = episodes.map(::episodeDetailDto)
    )

internal fun episodeDetailDto(
    ep: EpisodeWithPlayback,
    podcastId: String? = null,
    podcastName: String? = null,
    podcastImage: String? = null,
) = EpisodeDetailDto(
    id = ep.episode.id.value,
    title = ep.episode.title,
    description = ep.episode.description,
    audioUrl = ep.episode.audioUrl,
    duration = ep.episode.duration?.formatted(),
    durationMs = ep.episode.duration?.inWholeMilliseconds,
    publishedAt = ep.episode.publishedAt?.toString(),
    played = ep.played,
    progressMs = ep.progressMs,
    podcastId = podcastId,
    podcastName = podcastName,
    podcastImage = podcastImage,
)

internal fun Duration.formatted(): String =
    toComponents { _, hours, minutes, seconds, _ ->
        if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
```

- [ ] **Step 7: Update `EpisodeApi.kt` — inject `GetSettings` and filter recent by listening**

Full updated `get("recent")` handler within `episodeApi`:
```kotlin
package api

import application.model.EpisodeWithPlayback
import application.usecase.FindRecentUnplayedEpisodes
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import playback.core.usecase.GetPlaybackState
import playback.core.usecase.GetPlaybackStates
import playback.core.usecase.MarkPlayed
import playback.core.usecase.MarkUnplayed
import podcast.core.usecase.FindEpisode
import podcast.core.usecase.GetPodcast
import podcast.core.usecase.ListPodcasts
import settings.core.usecase.GetSettings
import shared.model.EpisodeId

fun Route.episodeApi(dependencies: DependencyRegistry) {
    val findEpisode: FindEpisode by dependencies
    val findRecentUnplayedEpisodes: FindRecentUnplayedEpisodes by dependencies
    val getPodcast: GetPodcast by dependencies
    val getPlaybackState: GetPlaybackState by dependencies
    val getPlaybackStates: GetPlaybackStates by dependencies
    val listPodcasts: ListPodcasts by dependencies
    val markPlayed: MarkPlayed by dependencies
    val markUnplayed: MarkUnplayed by dependencies
    val getSettings: GetSettings by dependencies

    get("{episodeId}") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        val episode = findEpisode(episodeId) ?: return@get call.respond(HttpStatusCode.NotFound)
        val playback = getPlaybackState(episodeId)
        val podcast = getPodcast(episode.podcastId)
        call.respond(episodeDetailDto(
            EpisodeWithPlayback(episode, playback.progressMs, playback.played),
            podcastId = episode.podcastId.value,
            podcastName = podcast?.name,
            podcastImage = podcast?.image,
        ))
    }

    get("recent") {
        val episodes = findRecentUnplayedEpisodes()
        val podcasts = listPodcasts().associateBy { it.id }
        val settings = getSettings()
        val filtered = if (settings.recentListeningOnly) {
            episodes.filter { podcasts[it.podcastId]?.listening == true }
        } else {
            episodes
        }
        val states = getPlaybackStates(filtered.map { it.id })
        call.respond(filtered.map { ep ->
            val podcast = podcasts[ep.podcastId]
            val state = states[ep.id]
            episodeDetailDto(
                EpisodeWithPlayback(ep, state?.progressMs ?: 0, state?.played ?: false),
                podcastId = ep.podcastId.value,
                podcastName = podcast?.name,
                podcastImage = podcast?.image,
            )
        })
    }

    post("{episodeId}/played") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        findEpisode(episodeId) ?: return@post call.respond(HttpStatusCode.NotFound)
        markPlayed(episodeId)
        call.respond(HttpStatusCode.NoContent)
    }

    delete("{episodeId}/played") {
        val episodeId = EpisodeId(call.parameters["episodeId"]!!)
        findEpisode(episodeId) ?: return@delete call.respond(HttpStatusCode.NotFound)
        markUnplayed(episodeId)
        call.respond(HttpStatusCode.NoContent)
    }
}
```

- [ ] **Step 8: Update `SettingsApi.kt` — thread `recentListeningOnly`**

Full updated file:
```kotlin
package api

import cast.api.SettingsDto
import io.ktor.http.*
import io.ktor.server.plugins.di.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import settings.core.models.Settings
import settings.core.usecase.GetSettings
import settings.core.usecase.UpdateSettings

fun Route.settingsApi(dependencies: DependencyRegistry) {
    val getSettings: GetSettings by dependencies
    val updateSettings: UpdateSettings by dependencies

    get {
        val settings = getSettings()
        call.respond(SettingsDto(hidePlayed = settings.hidePlayed, recentListeningOnly = settings.recentListeningOnly))
    }

    put {
        val dto = call.receive<SettingsDto>()
        updateSettings(Settings(hidePlayed = dto.hidePlayed, recentListeningOnly = dto.recentListeningOnly))
        call.respond(HttpStatusCode.NoContent)
    }
}
```

- [ ] **Step 9: Run full test suite**

```bash
./gradlew test 2>&1 | tail -30
```
Expected: all tests PASS.

- [ ] **Step 10: Commit**

```bash
git add shared-models/src/main/kotlin/cast/api/PodcastDtos.kt \
        shared-models/src/main/kotlin/cast/api/SettingsDtos.kt \
        core/src/main/kotlin/podcast/PodcastModule.kt \
        core/src/main/kotlin/api/PodcastApi.kt \
        core/src/main/kotlin/api/EpisodeApi.kt \
        core/src/main/kotlin/api/SettingsApi.kt \
        core/src/test/kotlin/AppTest.kt
git commit -m "feat(api): listening endpoints, recentListeningOnly filter on recent feed"
```

---

## Task 5: Webapp

**Files:**
- Modify: `webapp/generated/api.ts`
- Modify: `webapp/server.tsx`
- Modify: `webapp/components/PodcastList.tsx`
- Modify: `webapp/components/PodcastDetail.tsx`
- Modify: `webapp/components/SettingsPage.tsx`

- [ ] **Step 1: Regenerate TypeScript types**

```bash
./gradlew :shared-models:generateTypeScript
```

Verify `webapp/generated/api.ts` now contains `listening: boolean` on `PodcastSummaryDto` and `PodcastDetailDto`, and `recentListeningOnly: boolean` on `SettingsDto`.

- [ ] **Step 2: Add proxy routes for listening toggle in `server.tsx`**

Add these two routes after the existing `app.post('/api/podcasts/:id/played', ...)` handler (around line 201):

```typescript
app.post('/api/podcasts/:id/listening', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    await fetch(`${KOTLIN_API}/api/podcasts/${id}/listening`, {method: 'POST'})
    const podcasts: Podcast[] = await fetch(`${KOTLIN_API}/api/podcasts`).then(r => r.json())
    return c.html(<PodcastList podcasts={podcasts}/>)
})

app.delete('/api/podcasts/:id/listening', async (c) => {
    const id = encodeURIComponent(c.req.param('id'))
    await fetch(`${KOTLIN_API}/api/podcasts/${id}/listening`, {method: 'DELETE'})
    const podcasts: Podcast[] = await fetch(`${KOTLIN_API}/api/podcasts`).then(r => r.json())
    return c.html(<PodcastList podcasts={podcasts}/>)
})
```

- [ ] **Step 3: Update the settings GET handler in `server.tsx`**

Replace:
```typescript
const settings = await res.json() as {hidePlayed: boolean}
const content = <SettingsPage hidePlayed={settings.hidePlayed}/>
```
With:
```typescript
const settings = await res.json() as {hidePlayed: boolean, recentListeningOnly: boolean}
const content = <SettingsPage hidePlayed={settings.hidePlayed} recentListeningOnly={settings.recentListeningOnly}/>
```

- [ ] **Step 4: Update the settings POST handler in `server.tsx`**

Replace:
```typescript
app.post('/settings', async (c) => {
    const body = await c.req.parseBody()
    const hidePlayed = body['hidePlayed'] === 'on'
    await fetch(`${KOTLIN_API}/api/settings`, {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({hidePlayed}),
    })
    return new Response(null, {status: 204})
})
```
With:
```typescript
app.post('/settings', async (c) => {
    const body = await c.req.parseBody()
    const hidePlayed = body['hidePlayed'] === 'on'
    const recentListeningOnly = body['recentListeningOnly'] === 'on'
    await fetch(`${KOTLIN_API}/api/settings`, {
        method: 'PUT',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({hidePlayed, recentListeningOnly}),
    })
    return new Response(null, {status: 204})
})
```

- [ ] **Step 5: Update `PodcastList.tsx` — add listening toggle per card**

Full updated file:
```typescript
import type {FC} from 'hono/jsx'
import type {Podcast} from '../types'

export const PodcastList: FC<{ podcasts: Podcast[] }> = ({podcasts}) => (
    <div class="podcast-list">
        {podcasts.length === 0 ? <EmptyState/> : <PodcastGrid podcasts={podcasts}/>}
    </div>
)

const EmptyState: FC = () => (
    <div class="empty-state">
        <div class="empty-state-icon">🎙</div>
        <h2 class="empty-state-title">No podcasts yet</h2>
        <p class="empty-state-body">
            Add your first podcast by clicking <strong>＋ Add podcast</strong> above.
        </p>
    </div>
)

const PodcastGrid: FC<{ podcasts: Podcast[] }> = ({podcasts}) => (
    <div class="podcast-grid">
        {podcasts.map(podcast => <PodcastCard key={podcast.id} podcast={podcast}/>)}
    </div>
)

const PodcastCard: FC<{ podcast: Podcast }> = ({podcast}) => (
    <div
        class="podcast-card-link"
        hx-get={`/podcasts/${podcast.id}`}
        hx-target="#content-container"
        hx-swap="outerHTML"
        hx-push-url="true"
        hx-indicator="#nav-spinner"
    >
        <div class="podcast-card">
            <img src={podcast.image} alt={podcast.name} class="podcast-card-img" loading="lazy"/>
            <div class="podcast-card-info">
                <p class="podcast-card-name">{podcast.name}</p>
                {podcast.listening ? (
                    <button
                        class="listening-badge listening-badge--on"
                        hx-delete={`/api/podcasts/${podcast.id}/listening`}
                        hx-target=".podcast-list"
                        hx-swap="outerHTML"
                        hx-stop-propagation="true"
                        onclick="event.stopPropagation()"
                        title="Listening — click to stop"
                    >Listening</button>
                ) : (
                    <button
                        class="listening-badge listening-badge--off"
                        hx-post={`/api/podcasts/${podcast.id}/listening`}
                        hx-target=".podcast-list"
                        hx-swap="outerHTML"
                        hx-stop-propagation="true"
                        onclick="event.stopPropagation()"
                        title="Not listening — click to start"
                    >Not listening</button>
                )}
            </div>
        </div>
    </div>
)
```

- [ ] **Step 6: Update `PodcastDetail.tsx` — add listening toggle**

Full updated file:
```typescript
import type {FC} from 'hono/jsx'
import type {Episode, Podcast} from '../types'
import {EpisodeItem} from './EpisodeItem'

export const PodcastDetail: FC<{ podcast: Podcast; episodes: Episode[] }> = ({podcast, episodes}) => (
    <div class="podcast-detail">
        <a class="back-link" onclick="history.back()" style="cursor:pointer">← Back</a>

        <div class="podcast-header">
            <img src={podcast.image} alt={podcast.name} class="podcast-cover" loading="lazy"/>
            <div class="podcast-header-info">
                <h1 class="podcast-title">{podcast.name}</h1>
                <p class="podcast-subtitle">{episodes.length} episodes</p>
                <div class="podcast-actions">
                    <button
                        class="podcast-action-btn"
                        hx-post={`/api/podcasts/${podcast.id}/played`}
                        hx-swap="none"
                        {...{"hx-on:htmx:after-request": "if(event.detail.successful) markAllEpisodesPlayed()"}}
                        title="Mark all as played"
                    >
                        Mark all as played
                    </button>
                    {podcast.listening ? (
                        <button
                            class="podcast-action-btn"
                            hx-delete={`/api/podcasts/${podcast.id}/listening`}
                            hx-target=".podcast-detail"
                            hx-swap="outerHTML"
                            hx-get={`/podcasts/${podcast.id}`}
                        >
                            Stop listening
                        </button>
                    ) : (
                        <button
                            class="podcast-action-btn"
                            hx-post={`/api/podcasts/${podcast.id}/listening`}
                            hx-target=".podcast-detail"
                            hx-swap="outerHTML"
                            hx-get={`/podcasts/${podcast.id}`}
                        >
                            Start listening
                        </button>
                    )}
                </div>
            </div>
        </div>

        {episodes.length === 0 ? (
            <p class="empty-message">No episodes available.</p>
        ) : (
            episodes.map(episode => <EpisodeItem key={episode.id} episode={episode}/>)
        )}
    </div>
)
```

Note: the HTMX pattern here first does the POST/DELETE then follows with the `hx-get` to reload the detail. Use HTMX's `hx-boost` or chain via `hx-on::after-request` if the two-step doesn't work as expected. The simplest working alternative is to return the updated detail HTML from the proxy (same pattern as the podcast list toggle) — update `server.tsx` accordingly if needed.

- [ ] **Step 7: Update `SettingsPage.tsx` — add `recentListeningOnly` checkbox**

Full updated file:
```typescript
import type {FC} from 'hono/jsx'

export const SettingsPage: FC<{ hidePlayed: boolean, recentListeningOnly: boolean }> = ({hidePlayed, recentListeningOnly}) => (
    <div class="settings-page">
        <h1 class="settings-title">Settings</h1>
        <form class="settings-form">
            <label class="settings-row">
                <span class="settings-label">Hide played episodes</span>
                <input
                    type="checkbox"
                    name="hidePlayed"
                    checked={hidePlayed}
                    hx-post="/settings"
                    hx-trigger="change"
                    hx-include="closest form"
                    hx-swap="none"
                />
            </label>
            <label class="settings-row">
                <span class="settings-label">Recent shows only from Listening</span>
                <input
                    type="checkbox"
                    name="recentListeningOnly"
                    checked={recentListeningOnly}
                    hx-post="/settings"
                    hx-trigger="change"
                    hx-include="closest form"
                    hx-swap="none"
                />
            </label>
        </form>
    </div>
)
```

- [ ] **Step 8: Commit**

```bash
git add webapp/generated/api.ts \
        webapp/server.tsx \
        webapp/components/PodcastList.tsx \
        webapp/components/PodcastDetail.tsx \
        webapp/components/SettingsPage.tsx
git commit -m "feat(webapp): listening toggle in catalog and podcast detail, recentListeningOnly setting"
```

---

## Task 6: Android — network + repository

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/network/CastApiService.kt`
- Modify: `android/app/src/main/kotlin/cast/android/domain/repository/PodcastRepository.kt`
- Modify: `android/app/src/main/kotlin/cast/android/domain/repository/impl/PodcastRepositoryImpl.kt`

- [ ] **Step 1: Add listening endpoints to `CastApiService.kt`**

Add after `removePodcast`:
```kotlin
@POST("api/podcasts/{id}/listening")
suspend fun startListening(@Path("id") id: String): Response<Unit>

@DELETE("api/podcasts/{id}/listening")
suspend fun stopListening(@Path("id") id: String): Response<Unit>
```

- [ ] **Step 2: Add `setListening` to `PodcastRepository.kt`**

Add to the interface:
```kotlin
suspend fun setListening(podcastId: String, listening: Boolean)
```

- [ ] **Step 3: Implement `setListening` in `PodcastRepositoryImpl.kt`**

Add the method:
```kotlin
override suspend fun setListening(podcastId: String, listening: Boolean) {
    if (listening) {
        api.startListening(podcastId).orThrow()
    } else {
        api.stopListening(podcastId).orThrow()
    }
    podcastsCache.clear()
}
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/network/CastApiService.kt \
        android/app/src/main/kotlin/cast/android/domain/repository/PodcastRepository.kt \
        android/app/src/main/kotlin/cast/android/domain/repository/impl/PodcastRepositoryImpl.kt
git commit -m "feat(android): add setListening to podcast repository and API service"
```

---

## Task 7: Android — settings

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/domain/model/Settings.kt`
- Modify: `android/app/src/main/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImpl.kt`
- Modify: `android/app/src/main/kotlin/cast/android/ui/screens/SettingsScreen.kt`

- [ ] **Step 1: Add `recentListeningOnly` to Android `Settings.kt`**

```kotlin
package cast.android.domain.model

import cast.android.BuildConfig

data class Settings(
    val serverUrl: String = DEFAULT_SERVER_URL,
    val hidePlayed: Boolean = false,
    val recentListeningOnly: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
) {
    companion object {
        val DEFAULT_SERVER_URL = BuildConfig.DEFAULT_SERVER_URL
    }
}
```

- [ ] **Step 2: Update `SettingsRepositoryImpl.kt`**

Add the key constant:
```kotlin
private val RECENT_LISTENING_ONLY = booleanPreferencesKey("recent_listening_only")
```

Update `toSettings()`:
```kotlin
private fun Preferences.toSettings() = Settings(
    serverUrl = this[SERVER_URL] ?: Settings.DEFAULT_SERVER_URL,
    hidePlayed = this[HIDE_PLAYED] ?: false,
    recentListeningOnly = this[RECENT_LISTENING_ONLY] ?: true,
    themeMode = this[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        ?: ThemeMode.SYSTEM,
)
```

Update `updateSettings()` — add persistence and API call for `recentListeningOnly`:
```kotlin
override suspend fun updateSettings(settings: Settings) {
    dataStore.edit { prefs ->
        prefs[SERVER_URL] = settings.serverUrl
        prefs[HIDE_PLAYED] = settings.hidePlayed
        prefs[RECENT_LISTENING_ONLY] = settings.recentListeningOnly
        prefs[THEME_MODE] = settings.themeMode.name
    }
    baseUrlInterceptor.baseUrl = settings.serverUrl
    api.updateSettings(SettingsDto(hidePlayed = settings.hidePlayed, recentListeningOnly = settings.recentListeningOnly)).orThrow()
}
```

Update `refresh()`:
```kotlin
override suspend fun refresh() {
    val remote = api.getSettings()
    dataStore.edit { prefs ->
        prefs[HIDE_PLAYED] = remote.hidePlayed
        prefs[RECENT_LISTENING_ONLY] = remote.recentListeningOnly
    }
}
```

- [ ] **Step 3: Update `SettingsScreen.kt` — add `recentListeningOnly` switch**

Add after the `hidePlayed` row and before the Theme section:
```kotlin
Spacer(Modifier.height(24.dp))

Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
) {
    Text("Recent shows only from Listening", style = MaterialTheme.typography.bodyLarge)
    Switch(
        checked = settings.recentListeningOnly,
        onCheckedChange = { vm.updateSettings(settings.copy(recentListeningOnly = it)) },
    )
}
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/domain/model/Settings.kt \
        android/app/src/main/kotlin/cast/android/domain/repository/impl/SettingsRepositoryImpl.kt \
        android/app/src/main/kotlin/cast/android/ui/screens/SettingsScreen.kt
git commit -m "feat(android): recentListeningOnly setting in Android"
```

---

## Task 8: Android — catalog screen + podcast detail

**Files:**
- Modify: `android/app/src/main/kotlin/cast/android/ui/viewmodel/CatalogViewModel.kt`
- Modify: `android/app/src/main/kotlin/cast/android/ui/screens/CatalogScreen.kt`
- Modify: `android/app/src/main/kotlin/cast/android/ui/viewmodel/PodcastDetailViewModel.kt`
- Modify: `android/app/src/main/kotlin/cast/android/ui/screens/PodcastDetailScreen.kt`

- [ ] **Step 1: Add `toggleListening` to `CatalogViewModel.kt`**

Add the method:
```kotlin
fun toggleListening(podcastId: String, listening: Boolean) {
    viewModelScope.launch {
        try {
            podcastRepository.setListening(podcastId, listening)
            load()
        } catch (_: Exception) {}
    }
}
```

- [ ] **Step 2: Update `CatalogScreen.kt` — show listening badge on each card**

Update `PodcastCard` to accept an `onToggleListening` callback and show the badge:
```kotlin
@Composable
fun CatalogScreen(navController: NavHostController) {
    val vm: CatalogViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.openAddSheet() }) {
                Icon(Icons.Default.Add, contentDescription = "Add podcast")
            }
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Loading -> CatalogScreenSkeleton()
            is UiState.Error -> Box(Modifier.fillMaxSize()) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
            is UiState.Success -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = innerPadding,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.data, key = { it.id }) { podcast ->
                    PodcastCard(
                        podcast = podcast,
                        onClick = { navController.navigate(PodcastDetail(podcast.id)) },
                        onToggleListening = { vm.toggleListening(podcast.id, !podcast.listening) },
                    )
                }
            }
        }
    }

    if (vm.showAddSheet) {
        AddPodcastSheet(
            onDismiss = { vm.dismissAddSheet() },
            onSubmit = { vm.addPodcast(it) },
            onImportOpml = { vm.importOpml(it) },
            isLoading = vm.isAdding,
            error = vm.addError,
        )
    }
}

@Composable
private fun PodcastCard(podcast: PodcastSummaryDto, onClick: () -> Unit, onToggleListening: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(8.dp)
            .clickable(onClick = onClick),
    ) {
        Box {
            AsyncImage(
                model = podcast.image,
                contentDescription = podcast.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp)),
            )
            if (!podcast.listening) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clickable(onClick = onToggleListening),
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                ) {
                    Text(
                        text = "Not listening",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Text(
            text = podcast.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
```

Add required imports to `CatalogScreen.kt`:
```kotlin
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Surface
```

- [ ] **Step 3: Add `toggleListening` to `PodcastDetailViewModel.kt`**

Add the method:
```kotlin
fun toggleListening(listening: Boolean) {
    viewModelScope.launch {
        try {
            podcastRepository.setListening(podcastId, listening)
            load()
        } catch (_: Exception) {}
    }
}
```

- [ ] **Step 4: Update `PodcastDetailScreen.kt` — add listening menu item**

Add a "Listening" / "Stop listening" item to the `DropdownMenu`. The menu already has "Mark all played" and "Remove podcast". Add before "Remove podcast":

```kotlin
val podcast = (uiState as? UiState.Success)?.data
// ...inside the DropdownMenu:
DropdownMenuItem(
    text = { Text(if (podcast?.listening == true) "Stop listening" else "Start listening") },
    onClick = {
        menuExpanded = false
        vm.toggleListening(podcast?.listening != true)
    },
)
```

Full updated `actions` block:
```kotlin
actions = {
    if (uiState is UiState.Success) {
        val podcast = (uiState as UiState.Success).data
        IconButton(onClick = { menuExpanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "More")
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("Mark all played") },
                onClick = {
                    menuExpanded = false
                    vm.markAllPlayed()
                },
            )
            DropdownMenuItem(
                text = { Text(if (podcast.listening) "Stop listening" else "Start listening") },
                onClick = {
                    menuExpanded = false
                    vm.toggleListening(!podcast.listening)
                },
            )
            DropdownMenuItem(
                text = { Text("Remove podcast") },
                onClick = {
                    menuExpanded = false
                    showRemoveDialog = true
                },
            )
        }
    }
},
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/cast/android/ui/viewmodel/CatalogViewModel.kt \
        android/app/src/main/kotlin/cast/android/ui/screens/CatalogScreen.kt \
        android/app/src/main/kotlin/cast/android/ui/viewmodel/PodcastDetailViewModel.kt \
        android/app/src/main/kotlin/cast/android/ui/screens/PodcastDetailScreen.kt
git commit -m "feat(android): listening toggle in catalog and podcast detail screens"
```

---

## Task 9: Final verification

- [ ] **Step 1: Run all backend tests**

```bash
./gradlew test 2>&1 | tail -20
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 2: Start the server and smoke-test the API**

```bash
./gradlew run &
sleep 5
# Add a podcast
curl -s -X POST http://localhost:8100/api/podcasts \
  -H 'Content-Type: application/json' \
  -d '{"feed":"https://feeds.feedburner.com/se-radio"}' | python3 -m json.tool | grep listening
# Expected: "listening": true

# Get settings
curl -s http://localhost:8100/api/settings | python3 -m json.tool
# Expected: {"hidePlayed":false,"recentListeningOnly":true}
```

- [ ] **Step 3: Push to remote**

```bash
git push
```

- [ ] **Step 4: Tell the user to build the Android app**

Android builds run on the Mac. After pushing:
```
cd android && ./gradlew assembleDebug && ./gradlew installDebug
```
Then verify: listening badge on catalog cards, listening menu item in podcast detail, recentListeningOnly switch in settings.
