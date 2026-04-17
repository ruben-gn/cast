package configuration

import java.sql.Connection
import java.sql.DriverManager


fun installDatabase(): Connection {
    val connection = DriverManager.getConnection("jdbc:sqlite:podcasts.db")

    connection.createStatement().use { statement ->
        statement.executeUpdate(CREATE_PODCASTS_TABLE)
        statement.executeUpdate(CREATE_EPISODES_TABLE)
    }

    return connection
}

val CREATE_PODCASTS_TABLE = """
            CREATE TABLE IF NOT EXISTS podcasts (
                id TEXT PRIMARY KEY,
                url TEXT NOT NULL,
                name TEXT NOT NULL,
                image TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
        """.trimIndent()

val CREATE_EPISODES_TABLE = """
            CREATE TABLE IF NOT EXISTS episodes (
                id TEXT PRIMARY KEY,
                podcast_id TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                audio_url TEXT NOT NULL,
                duration TEXT,
                published_at TEXT,
                FOREIGN KEY (podcast_id) REFERENCES podcasts(id) ON DELETE CASCADE
            )
        """.trimIndent()