package configuration

import kotlinx.coroutines.sync.Mutex
import java.sql.Connection

class DatabaseContext(val connection: Connection, val mutex: Mutex = Mutex())
