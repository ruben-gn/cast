package settings.adapters.persistence

import configuration.CREATE_SETTINGS_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import settings.core.models.Settings
import java.sql.DriverManager

class SQLiteSettingsPersistenceIT : DescribeSpec({
    lateinit var db: SingleConnectionProvider
    lateinit var persistence: SQLiteSettingsPersistence

    beforeEach {
        db = SingleConnectionProvider(DriverManager.getConnection("jdbc:sqlite::memory:"))
        persistence = SQLiteSettingsPersistence(db)
        db.withConnection { conn ->
            conn.createStatement().use { it.execute(CREATE_SETTINGS_TABLE) }
        }
    }

    afterEach { db.close() }

    it("returns hidePlayed = false when no row exists") {
        persistence.get().hidePlayed shouldBe false
    }

    it("persists hidePlayed = true") {
        persistence.update(Settings(hidePlayed = true))
        persistence.get().hidePlayed shouldBe true
    }

    it("updates an existing row") {
        persistence.update(Settings(hidePlayed = true))
        persistence.update(Settings(hidePlayed = false))
        persistence.get().hidePlayed shouldBe false
    }
})
