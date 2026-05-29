package cast.android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.PodcastRepository
import cast.android.domain.repository.QueueRepository
import cast.android.ui.UiState
import cast.android.ui.nav.PodcastDetail
import cast.api.PodcastDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val podcastId: String = savedStateHandle.toRoute<PodcastDetail>().podcastId

    private val _uiState = MutableStateFlow<UiState<PodcastDetailDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<PodcastDetailDto>> = _uiState.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(podcastRepository.getPodcast(podcastId))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load podcast")
            }
        }
    }

    fun addToQueue(episodeId: String) {
        viewModelScope.launch {
            try { queueRepository.addToQueue(episodeId) } catch (_: Exception) {}
        }
    }

    fun markAllPlayed() {
        viewModelScope.launch {
            try {
                podcastRepository.markAllPlayed(podcastId)
                load()
            } catch (_: Exception) {}
        }
    }

    fun togglePlayed(episodeId: String, newPlayed: Boolean) {
        viewModelScope.launch {
            try {
                if (newPlayed) episodeRepository.markPlayed(episodeId)
                else episodeRepository.markUnplayed(episodeId)
                val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
                _uiState.value = UiState.Success(
                    current.copy(
                        episodes = current.episodes.map { ep ->
                            if (ep.id == episodeId) ep.copy(played = newPlayed) else ep
                        }
                    )
                )
            } catch (_: Exception) {}
        }
    }
}
