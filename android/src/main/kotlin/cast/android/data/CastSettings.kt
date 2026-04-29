package cast.android.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
        const val DEFAULT_SERVER_URL = "http://cast.local:8100"
    }

    val serverUrl: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL] ?: DEFAULT_SERVER_URL
    }

    suspend fun setServerUrl(url: String) {
        dataStore.edit { it[KEY_SERVER_URL] = url }
    }
}
