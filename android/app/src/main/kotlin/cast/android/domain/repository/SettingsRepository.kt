package cast.android.domain.repository

import cast.android.domain.model.Settings
import cast.android.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun updateSettings(settings: Settings)

    /** Persist the theme preference only. Purely local — never hits the server. */
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun refresh()
}
