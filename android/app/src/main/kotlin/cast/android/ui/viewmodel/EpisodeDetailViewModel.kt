package cast.android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.QueueRepository
import cast.android.ui.UiState
import cast.android.ui.nav.EpisodeDetail
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val episodeId: String = savedStateHandle.toRoute<EpisodeDetail>().episodeId

    private val _uiState = MutableStateFlow<UiState<EpisodeDetailDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<EpisodeDetailDto>> = _uiState.asStateFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            _uiState.value = try {
                UiState.Success(episodeRepository.getEpisode(episodeId))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load episode")
            }
        }
    }

    fun addToQueue() {
        viewModelScope.launch {
            try { queueRepository.addToQueue(episodeId) } catch (_: Exception) {}
        }
    }

    fun togglePlayed(newPlayed: Boolean) {
        viewModelScope.launch {
            try {
                if (newPlayed) episodeRepository.markPlayed(episodeId)
                else episodeRepository.markUnplayed(episodeId)
                val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
                _uiState.value = UiState.Success(current.copy(played = newPlayed))
            } catch (_: Exception) {}
        }
    }
}
