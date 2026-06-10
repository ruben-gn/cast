package settings.adapters.persistence

import configuration.CREATE_SETTINGS_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import settings.core.ports.settingsPersistenceContract
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

    include(settingsPersistenceContract { persistence })
})
