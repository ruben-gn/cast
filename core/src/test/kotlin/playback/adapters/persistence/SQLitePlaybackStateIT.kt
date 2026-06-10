package playback.adapters.persistence

import configuration.CREATE_PLAYBACK_STATE_TABLE
import configuration.SingleConnectionProvider
import io.kotest.core.spec.style.DescribeSpec
import playback.core.ports.playbackPersistenceContract
import java.sql.DriverManager

class SQLitePlaybackStateIT : DescribeSpec({

    lateinit var db: SingleConnectionProvider
    lateinit var persistence: SQLitePlaybackState

    beforeEach {
        db = SingleConnectionProvider(DriverManager.getConnection("jdbc:sqlite::memory:"))
        persistence = SQLitePlaybackState(db)
        db.withConnection { conn ->
            conn.createStatement().use { it.execute(CREATE_PLAYBACK_STATE_TABLE) }
        }
    }

    afterEach { db.close() }

    include(playbackPersistenceContract { persistence })
})
