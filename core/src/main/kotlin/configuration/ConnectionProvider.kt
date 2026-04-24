package configuration

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.sql.Connection

interface ConnectionProvider {
    suspend fun <T> withConnection(block: (Connection) -> T): T
}

class SingleConnectionProvider(private val connection: Connection) : ConnectionProvider, AutoCloseable {
    private val mutex = Mutex()

    override suspend fun <T> withConnection(block: (Connection) -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                block(connection)
            }
        }

    override fun close() = connection.close()
}
