package settings.adapters.persistence

import configuration.ConnectionProvider
import settings.core.models.Settings
import settings.core.ports.SettingsPersistence

class SQLiteSettingsPersistence(private val db: ConnectionProvider) : SettingsPersistence {

    override suspend fun get(): Settings = db.withConnection { conn ->
        conn.prepareStatement("SELECT value FROM settings WHERE key = ?").use { stmt ->
            stmt.setString(1, "hide_played")
            val rs = stmt.executeQuery()
            Settings(hidePlayed = if (rs.next()) rs.getString("value") == "true" else false)
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
            }
        }
    }
}
