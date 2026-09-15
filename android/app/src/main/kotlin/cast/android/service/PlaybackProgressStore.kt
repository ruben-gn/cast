package cast.android.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Device-local persistence the playback brain depends on: per-episode resume progress and the
 * last-played episode id. Extracted behind an interface so [QueuePlaybackListener] can be driven
 * in a test with a trivial in-memory fake instead of a real [DataStore].
 *
 * Writes are fire-and-forget (the server is the source of truth; these are only a head-start cache).
 */
interface PlaybackProgressStore {
    suspend fun cachedProgressMs(episodeId: String): Long?
    fun cacheProgress(episodeId: String, progressMs: Long, atMillis: Long)
    fun clearCachedProgress(episodeId: String)
    fun markProgressPending(episodeId: String)
    /** Retires the entry only if [atMillis] is still its newest recorded position. */
    fun clearProgressPending(episodeId: String, atMillis: Long)
    fun markEndedPending(episodeId: String)
    fun clearEndedPending(episodeId: String)
    suspend fun pendingSync(): PendingSync
    fun rememberLastEpisode(episodeId: String)
    fun clearLastEpisode()
}

data class PendingProgress(val episodeId: String, val progressMs: Long, val atMillis: Long)
data class PendingSync(val progress: List<PendingProgress>, val endedEpisodeIds: List<String>)

/**
 * Production [PlaybackProgressStore] backed by the app's preferences [DataStore]. The fire-and-forget
 * writes are launched on [scope]; [PlaybackService] passes its IO-bound library scope so they never
 * block the player thread.
 */
class DataStorePlaybackProgressStore(
    private val dataStore: DataStore<Preferences>,
    private val scope: CoroutineScope,
) : PlaybackProgressStore {

    private fun progressKey(episodeId: String) = longPreferencesKey("progress_$episodeId")
    private fun progressAtKey(episodeId: String) = longPreferencesKey("progress_at_$episodeId")
    private fun progressPendingKey(episodeId: String) = booleanPreferencesKey("progress_pending_$episodeId")
    private fun endedPendingKey(episodeId: String) = booleanPreferencesKey("ended_pending_$episodeId")

    override suspend fun cachedProgressMs(episodeId: String): Long? =
        runCatching { dataStore.data.first()[progressKey(episodeId)] }.getOrNull()

    override fun cacheProgress(episodeId: String, progressMs: Long, atMillis: Long) {
        if (progressMs <= 0L) return
        scope.launch {
            runCatching {
                dataStore.edit {
                    it[progressKey(episodeId)] = progressMs
                    it[progressAtKey(episodeId)] = atMillis
                }
            }
        }
    }

    override fun clearCachedProgress(episodeId: String) {
        scope.launch {
            runCatching {
                dataStore.edit {
                    it.remove(progressKey(episodeId))
                    it.remove(progressAtKey(episodeId))
                    // A finished episode's position is moot; the ended flag carries it from here.
                    it.remove(progressPendingKey(episodeId))
                }
            }
        }
    }

    override fun markProgressPending(episodeId: String) {
        scope.launch {
            runCatching {
                // Checked first because this runs once per second while offline, and every edit
                // rewrites the whole preferences file.
                if (dataStore.data.first()[progressPendingKey(episodeId)] != true)
                    dataStore.edit { it[progressPendingKey(episodeId)] = true }
            }
        }
    }

    override fun clearProgressPending(episodeId: String, atMillis: Long) {
        scope.launch {
            runCatching {
                val prefs = dataStore.data.first()
                if (prefs[progressPendingKey(episodeId)] == true && prefs[progressAtKey(episodeId)] == atMillis)
                    dataStore.edit { it.remove(progressPendingKey(episodeId)) }
            }
        }
    }

    override fun markEndedPending(episodeId: String) {
        scope.launch { runCatching { dataStore.edit { it[endedPendingKey(episodeId)] = true } } }
    }

    override fun clearEndedPending(episodeId: String) {
        scope.launch { runCatching { dataStore.edit { it.remove(endedPendingKey(episodeId)) } } }
    }

    override suspend fun pendingSync(): PendingSync {
        val prefs = dataStore.data.first()
        val progress = prefs.asMap().keys
            .filter { it.name.startsWith("progress_pending_") }
            .mapNotNull { key ->
                val episodeId = key.name.removePrefix("progress_pending_")
                val atMillis = prefs[progressAtKey(episodeId)] as? Long ?: return@mapNotNull null
                val progressMs = prefs[progressKey(episodeId)] as? Long ?: return@mapNotNull null
                PendingProgress(episodeId, progressMs, atMillis)
            }
        val endedEpisodeIds = prefs.asMap().keys
            .filter { it.name.startsWith("ended_pending_") }
            .map { it.name.removePrefix("ended_pending_") }
        return PendingSync(progress, endedEpisodeIds)
    }

    override fun rememberLastEpisode(episodeId: String) {
        scope.launch { runCatching { dataStore.edit { it[PlaybackService.LAST_EPISODE_ID] = episodeId } } }
    }

    override fun clearLastEpisode() {
        scope.launch { runCatching { dataStore.edit { it.remove(PlaybackService.LAST_EPISODE_ID) } } }
    }
}
