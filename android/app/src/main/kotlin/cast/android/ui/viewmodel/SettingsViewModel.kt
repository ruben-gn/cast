package cast.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cast.android.domain.model.Settings
import cast.android.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    init {
        viewModelScope.launch { runCatching { settingsRepository.refresh() } }
    }

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            val url = settings.serverUrl.trim()
            val normalizedUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
            settingsRepository.updateSettings(settings.copy(serverUrl = normalizedUrl))
        }
    }
}
