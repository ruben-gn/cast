package cast.android.ui.viewmodel

import cast.android.domain.model.Settings
import cast.android.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow

class FakeSettingsRepository : SettingsRepository {
    override val settings = MutableStateFlow(Settings())
    var refreshCount = 0
    override suspend fun updateSettings(settings: Settings) { this.settings.value = settings }
    override suspend fun refresh() { refreshCount++ }
}
