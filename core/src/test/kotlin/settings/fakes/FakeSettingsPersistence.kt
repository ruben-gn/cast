package settings.fakes

import settings.core.models.Settings
import settings.core.ports.SettingsPersistence

class FakeSettingsPersistence(initial: Settings = Settings(hidePlayed = false)) : SettingsPersistence {
    var current = initial

    override suspend fun get() = current
    override suspend fun update(settings: Settings) { current = settings }
}
