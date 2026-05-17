package cast.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cast.android.domain.repository.EpisodeRepository
import cast.android.ui.UiState
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentViewModel @Inject constructor(
    private val episodeRepository: EpisodeRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<EpisodeDetailDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<EpisodeDetailDto>>> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(episodeRepository.getRecentEpisodes())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load episodes")
            }
        }
    }

    fun markPlayed(episodeId: String) {
        viewModelScope.launch {
            try {
                episodeRepository.markPlayed(episodeId)
                val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
                _uiState.value = UiState.Success(current.filterNot { it.id == episodeId })
            } catch (_: Exception) {}
        }
    }
}
