package configuration

import io.ktor.server.application.*
import io.ktor.server.plugins.di.dependencies
import java.sql.DriverManager

fun Application.installDatabase() {
    val connection = DriverManager.getConnection("jdbc:sqlite:podcasts.db")

    connection.createStatement().use { statement ->
        statement.executeUpdate(CREATE_PODCASTS_TABLE)
        statement.executeUpdate(CREATE_EPISODES_TABLE)
        statement.executeUpdate(CREATE_PLAYBACK_STATE_TABLE)
    }

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
        created TEXT NOT NULL,
        updated TEXT NOT NULL
    )
""".trimIndent()

val CREATE_EPISODES_TABLE = """
    CREATE TABLE IF NOT EXISTS episodes (
        id TEXT PRIMARY KEY,
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
        updated_at TEXT NOT NULL
    );
""".trimIndent()