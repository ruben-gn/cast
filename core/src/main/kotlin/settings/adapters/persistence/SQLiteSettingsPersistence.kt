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
                recentListeningOnly = map["recent_listening_only"] != "false",
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
