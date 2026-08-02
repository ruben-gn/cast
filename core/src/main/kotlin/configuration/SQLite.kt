package configuration

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import java.sql.DriverManager

fun Application.installDatabase() {
    val dbPath = System.getenv("DB_PATH") ?: "podcasts.db"
    val connection = DriverManager.getConnection("jdbc:sqlite:$dbPath")

    connection.createStatement().use { statement ->
        statement.executeUpdate(CREATE_PODCASTS_TABLE)
        statement.executeUpdate(CREATE_EPISODES_TABLE)
        statement.executeUpdate(CREATE_PLAYBACK_STATE_TABLE)
        statement.executeUpdate(CREATE_QUEUE_TABLE)
        statement.executeUpdate(CREATE_SETTINGS_TABLE)
        statement.executeUpdate(CREATE_SERIES_RULES_TABLE)
    }

    // Add `listening` column to existing databases that pre-date this feature.
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

val CREATE_EPISODES_TABLE = """
    CREATE TABLE IF NOT EXISTS episodes (
        id TEXT PRIMARY KEY,
        guid TEXT NOT NULL UNIQUE,
        podcast_id TEXT NOT NULL,
        title TEXT NOT NULL,
        description TEXT NOT NULL,
        audio_url TEXT NOT NULL,
        duration INTEGER,
        published_at TEXT,
        FOREIGN KEY (podcast_id) REFERENCES podcasts(id) ON DELETE CASCADE
    )
""".trimIndent()

val CREATE_PLAYBACK_STATE_TABLE = """
    CREATE TABLE IF NOT EXISTS playback_state (
        episode_id TEXT PRIMARY KEY,
        progress_ms INTEGER NOT NULL,
        updated_at TEXT NOT NULL,
        played INTEGER NOT NULL DEFAULT 0
    );
""".trimIndent()

val CREATE_QUEUE_TABLE = """
    CREATE TABLE IF NOT EXISTS queue (
        position INTEGER NOT NULL,
        episode_id TEXT NOT NULL UNIQUE
    )
""".trimIndent()

val CREATE_SETTINGS_TABLE = """
    CREATE TABLE IF NOT EXISTS settings (
        key TEXT PRIMARY KEY,
        value TEXT NOT NULL
    )
""".trimIndent()

val CREATE_SERIES_RULES_TABLE = """
    CREATE TABLE IF NOT EXISTS series_rules (
        podcast_id TEXT NOT NULL,
        name TEXT NOT NULL,
        PRIMARY KEY (podcast_id, name)
    )
""".trimIndent()