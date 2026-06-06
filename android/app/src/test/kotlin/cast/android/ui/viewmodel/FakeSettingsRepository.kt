package cast.android.ui.viewmodel

import cast.android.domain.model.Settings
import cast.android.domain.model.ThemeMode
import cast.android.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(Settings())
    var refreshCount = 0
    override suspend fun updateSettings(settings: Settings) { this.settings.value = settings }
    override suspend fun updateThemeMode(mode: ThemeMode) {
        this.settings.value = this.settings.value.copy(themeMode = mode)
    }
    override suspend fun refresh() { refreshCount++ }
}
