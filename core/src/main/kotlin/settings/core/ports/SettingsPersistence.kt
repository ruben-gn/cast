package settings.core.ports

import settings.core.models.Settings

interface SettingsPersistence {
    suspend fun get(): Settings
    suspend fun update(settings: Settings)
}
