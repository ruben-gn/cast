package cast.android.domain.repository.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import cast.android.domain.model.Settings
import cast.android.domain.model.ThemeMode
import cast.android.domain.repository.SettingsRepository
import cast.android.network.BaseUrlInterceptor
import cast.android.network.CastApiService
import cast.android.network.orThrow
import cast.api.SettingsDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val baseUrlInterceptor: BaseUrlInterceptor,
    private val api: CastApiService,
) : SettingsRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // Prime the interceptor from persisted settings once at construction so every request targets
        // the configured server. Updating it here (and in [updateSettings]) keeps the base URL in sync
        // explicitly, rather than as a side effect that only fires while [settings] is being collected.
        scope.launch { baseUrlInterceptor.baseUrl = currentSettings().serverUrl }
    }

    override val settings: Flow<Settings> = dataStore.data.map { it.toSettings() }

    override suspend fun updateSettings(settings: Settings) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = settings.serverUrl
            prefs[HIDE_PLAYED] = settings.hidePlayed
            prefs[THEME_MODE] = settings.themeMode.name
        }
        baseUrlInterceptor.baseUrl = settings.serverUrl
        api.updateSettings(SettingsDto(hidePlayed = settings.hidePlayed)).orThrow()
    }

    override suspend fun updateThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs -> prefs[THEME_MODE] = mode.name }
    }

    override suspend fun refresh() {
        val remote = api.getSettings()
        dataStore.edit { prefs -> prefs[HIDE_PLAYED] = remote.hidePlayed }
    }

    private suspend fun currentSettings(): Settings = dataStore.data.first().toSettings()

    private fun Preferences.toSettings() = Settings(
        serverUrl = this[SERVER_URL] ?: Settings.DEFAULT_SERVER_URL,
        hidePlayed = this[HIDE_PLAYED] ?: false,
        themeMode = this[THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM,
    )

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val HIDE_PLAYED = booleanPreferencesKey("hide_played")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
