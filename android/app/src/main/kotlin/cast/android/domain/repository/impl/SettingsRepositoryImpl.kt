package cast.android.domain.repository.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cast.android.domain.model.Settings
import cast.android.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            serverUrl = prefs[SERVER_URL] ?: Settings.DEFAULT_SERVER_URL,
            hidePlayed = prefs[HIDE_PLAYED] ?: false,
        )
    }

    override suspend fun updateSettings(settings: Settings) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = settings.serverUrl
            prefs[HIDE_PLAYED] = settings.hidePlayed
        }
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val HIDE_PLAYED = booleanPreferencesKey("hide_played")
    }
}
