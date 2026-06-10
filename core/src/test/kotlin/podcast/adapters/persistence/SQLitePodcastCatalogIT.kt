package podcast.adapters.persistence

import configuration.CREATE_EPISODES_TABLE
import configuration.CREATE_PODCASTS_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import podcast.core.ports.podcastCatalogContract
import java.sql.DriverManager

class SQLitePodcastCatalogIT : DescribeSpec({

    lateinit var db: SingleConnectionProvider
    lateinit var catalog: SQLitePodcastCatalog

    beforeEach {
        db = SingleConnectionProvider(DriverManager.getConnection("jdbc:sqlite::memory:"))
        catalog = SQLitePodcastCatalog(db)
        db.withConnection { conn ->
            conn.createStatement().use { it.execute(CREATE_PODCASTS_TABLE) }
            conn.createStatement().use { it.execute(CREATE_EPISODES_TABLE) }
        }
    }

    afterEach { db.close() }

    include(podcastCatalogContract { catalog })
})
