package cast.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cast.android.data.CastSettings
import cast.android.data.PodcastRepository
import cast.android.ui.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: CastSettings,
    private val repository: PodcastRepository,
) : ViewModel() {

    val serverUrl: StateFlow<String> = settings.serverUrl.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CastSettings.DEFAULT_SERVER_URL,
    )

    private val _testState = MutableStateFlow<UiState<Unit>?>(null)
    val testState: StateFlow<UiState<Unit>?> = _testState.asStateFlow()

    fun setUrl(url: String) {
        viewModelScope.launch { settings.setServerUrl(url) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _testState.value = UiState.Loading
            _testState.value = try {
                repository.listPodcasts()
                UiState.Success(Unit)
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Connection failed")
            }
        }
    }
}
