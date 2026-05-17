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

    describe("get") {
        it("returns defaults when no settings have been saved") {
            persistence.get() shouldBe Settings(hidePlayed = false)
        }
    }

    describe("update") {
        it("persists hidePlayed = true") {
            persistence.update(Settings(hidePlayed = true))

            persistence.get() shouldBe Settings(hidePlayed = true)
        }

        it("persists hidePlayed = false after it was true") {
            persistence.update(Settings(hidePlayed = true))
            persistence.update(Settings(hidePlayed = false))

            persistence.get() shouldBe Settings(hidePlayed = false)
        }

        it("upserts on repeated calls") {
            persistence.update(Settings(hidePlayed = true))
            persistence.update(Settings(hidePlayed = true))

            persistence.get() shouldBe Settings(hidePlayed = true)
        }
    }
})
