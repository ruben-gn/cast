package settings.core.usecase

import settings.core.models.Settings
import settings.core.ports.SettingsPersistence

class GetSettings(private val persistence: SettingsPersistence) {
    suspend operator fun invoke(): Settings = persistence.get()
}
