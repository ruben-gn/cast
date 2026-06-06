package cast.android.domain.repository

import cast.android.domain.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun updateSettings(settings: Settings)
    suspend fun refresh()
}
