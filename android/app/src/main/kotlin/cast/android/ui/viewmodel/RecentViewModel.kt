package cast.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.QueueRepository
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
    private val queueRepository: QueueRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<EpisodeDetailDto>>>(
        episodeRepository.cachedRecentEpisodes()?.let { UiState.Success(it) } ?: UiState.Loading
    )
    val uiState: StateFlow<UiState<List<EpisodeDetailDto>>> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            if (_uiState.value !is UiState.Success) _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(episodeRepository.getRecentEpisodes())
            } catch (e: Exception) {
                // Keep showing cached data on refresh failure; only surface Error on cold start.
                if (_uiState.value is UiState.Success) _uiState.value
                else UiState.Error(e.message ?: "Failed to load episodes")
            }
        }
    }

    fun addToQueue(episodeId: String) {
        viewModelScope.launch {
            try { queueRepository.addToQueue(episodeId) } catch (_: Exception) {}
        }
    }

    fun onEpisodeCompleted(episodeId: String) {
        val current = (_uiState.value as? UiState.Success)?.data ?: return
        _uiState.value = UiState.Success(current.filterNot { it.id == episodeId })
    }

    fun togglePlayed(episodeId: String, newPlayed: Boolean) {
        viewModelScope.launch {
            try {
                if (newPlayed) episodeRepository.markPlayed(episodeId)
                else episodeRepository.markUnplayed(episodeId)
                val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
                val updated = if (newPlayed) {
                    current.filterNot { it.id == episodeId }
                } else {
                    current.map { ep -> if (ep.id == episodeId) ep.copy(played = false) else ep }
                }
                _uiState.value = UiState.Success(updated)
            } catch (_: Exception) {}
        }
    }
}
