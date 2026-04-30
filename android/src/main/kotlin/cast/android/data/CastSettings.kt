package cast.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastSettings @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_LAST_EPISODE_ID = stringPreferencesKey("last_episode_id")
        private val KEY_LAST_EPISODE_TITLE = stringPreferencesKey("last_episode_title")
        private val KEY_LAST_EPISODE_URL = stringPreferencesKey("last_episode_url")
        private val KEY_LAST_PODCAST_IMAGE = stringPreferencesKey("last_podcast_image")
        private val KEY_LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        const val DEFAULT_SERVER_URL = "http://cast.local:8100"
    }

    val serverUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    val lastEpisodeId: Flow<String?> = dataStore.data.map { it[KEY_LAST_EPISODE_ID] }
    val lastEpisodeTitle: Flow<String?> = dataStore.data.map { it[KEY_LAST_EPISODE_TITLE] }
    val lastEpisodeUrl: Flow<String?> = dataStore.data.map { it[KEY_LAST_EPISODE_URL] }
    val lastPodcastImage: Flow<String?> = dataStore.data.map { it[KEY_LAST_PODCAST_IMAGE] }
    val lastPositionMs: Flow<Long> = dataStore.data.map { it[KEY_LAST_POSITION_MS] ?: 0L }

    suspend fun setServerUrl(url: String) {
        dataStore.edit { it[KEY_SERVER_URL] = url }
    }

    suspend fun saveLastPlayed(episodeId: String, title: String, audioUrl: String, imageUrl: String, progressMs: Long = 0L) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_EPISODE_ID] = episodeId
            prefs[KEY_LAST_EPISODE_TITLE] = title
            prefs[KEY_LAST_EPISODE_URL] = audioUrl
            prefs[KEY_LAST_PODCAST_IMAGE] = imageUrl
            prefs[KEY_LAST_POSITION_MS] = progressMs
        }
    }

    suspend fun saveLastPosition(progressMs: Long) {
        dataStore.edit { it[KEY_LAST_POSITION_MS] = progressMs }
    }
}
