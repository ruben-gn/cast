package cast.android.domain.repository.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Per-episode downloadedAt/lastPlayedAt timestamps backing the 2-week "not touched" cleanup
 * sweep. Keyed by episode id in the shared preferences [DataStore]. Writes are fire-and-forget.
 */
@Singleton
class DownloadTimestampStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun downloadedAtKey(episodeId: String) = longPreferencesKey("downloaded_at_$episodeId")
    private fun lastPlayedAtKey(episodeId: String) = longPreferencesKey("last_played_at_$episodeId")

    /** Records the completion moment once; later calls for the same still-downloaded episode are no-ops. */
    fun markDownloadedIfAbsent(episodeId: String, atMillis: Long = System.currentTimeMillis()) {
        scope.launch {
            runCatching {
                dataStore.edit { prefs ->
                    val key = downloadedAtKey(episodeId)
                    if (prefs[key] == null) prefs[key] = atMillis
                }
            }
        }
    }

    fun markPlayed(episodeId: String, atMillis: Long = System.currentTimeMillis()) {
        scope.launch { runCatching { dataStore.edit { it[lastPlayedAtKey(episodeId)] = atMillis } } }
    }

    fun clear(episodeId: String) {
        scope.launch {
            runCatching {
                dataStore.edit {
                    it.remove(downloadedAtKey(episodeId))
                    it.remove(lastPlayedAtKey(episodeId))
                }
            }
        }
    }

    /** More recent of downloadedAt/lastPlayedAt, i.e. the moment the 2-week clock resets from. */
    suspend fun lastTouchedAt(episodeId: String): Long? {
        val prefs = dataStore.data.first()
        val downloadedAt = prefs[downloadedAtKey(episodeId)]
        val lastPlayedAt = prefs[lastPlayedAtKey(episodeId)]
        return maxOf(downloadedAt ?: 0L, lastPlayedAt ?: 0L).takeIf { it > 0L }
    }
}
