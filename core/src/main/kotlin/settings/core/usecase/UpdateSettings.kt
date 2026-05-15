package settings.core.usecase

import settings.core.models.Settings
import settings.core.ports.SettingsPersistence

class UpdateSettings(private val persistence: SettingsPersistence) {
    suspend operator fun invoke(settings: Settings) = persistence.update(settings)
}
