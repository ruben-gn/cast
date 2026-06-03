package cast.android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.QueueRepository
import cast.android.ui.UiState
import cast.android.ui.nav.EpisodeDetail
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EpisodeDetailViewModel @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    savedStateHandle: SavedStateHandle,
) : LoadableViewModel<EpisodeDetailDto>(UiState.Loading) {

    private val episodeId: String = savedStateHandle.toRoute<EpisodeDetail>().episodeId

    init { load() }

    private fun load() = load("Failed to load episode") { episodeRepository.getEpisode(episodeId) }

    fun addToQueue() {
        viewModelScope.launch {
            try { queueRepository.addToQueue(episodeId) } catch (_: Exception) {}
        }
    }

    fun togglePlayed(newPlayed: Boolean) {
        viewModelScope.launch {
            try {
                episodeRepository.setPlayed(episodeId, newPlayed)
                val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
                _uiState.value = UiState.Success(current.copy(played = newPlayed))
            } catch (_: Exception) {}
        }
    }
}
