package cast.android.service

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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
    fun cacheProgress(episodeId: String, progressMs: Long)
    fun clearCachedProgress(episodeId: String)
    fun rememberLastEpisode(episodeId: String)
    fun clearLastEpisode()
}

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

    override suspend fun cachedProgressMs(episodeId: String): Long? =
        runCatching { dataStore.data.first()[progressKey(episodeId)] }.getOrNull()

    override fun cacheProgress(episodeId: String, progressMs: Long) {
        if (progressMs <= 0L) return
        scope.launch { runCatching { dataStore.edit { it[progressKey(episodeId)] = progressMs } } }
    }

    override fun clearCachedProgress(episodeId: String) {
        scope.launch { runCatching { dataStore.edit { it.remove(progressKey(episodeId)) } } }
    }

    override fun rememberLastEpisode(episodeId: String) {
        scope.launch { runCatching { dataStore.edit { it[PlaybackService.LAST_EPISODE_ID] = episodeId } } }
    }

    override fun clearLastEpisode() {
        scope.launch { runCatching { dataStore.edit { it.remove(PlaybackService.LAST_EPISODE_ID) } } }
    }
}
