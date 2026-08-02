package podcast.adapters.persistence

import configuration.CREATE_SERIES_RULES_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import podcast.core.ports.seriesRulePersistenceContract
import java.sql.DriverManager

class SQLiteSeriesRulePersistenceIT : DescribeSpec({

    lateinit var db: SingleConnectionProvider
    lateinit var persistence: SQLiteSeriesRulePersistence

    beforeEach {
        db = SingleConnectionProvider(DriverManager.getConnection("jdbc:sqlite::memory:"))
        persistence = SQLiteSeriesRulePersistence(db)
        db.withConnection { conn ->
            conn.createStatement().use { it.execute(CREATE_SERIES_RULES_TABLE) }
        }
    }

    afterEach { db.close() }

    include(seriesRulePersistenceContract { persistence })
})
