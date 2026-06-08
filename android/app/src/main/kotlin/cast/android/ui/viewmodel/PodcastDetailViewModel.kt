package cast.android.ui.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.PodcastRepository
import cast.android.domain.repository.QueueRepository
import cast.android.ui.UiState
import cast.android.ui.nav.PodcastDetail
import cast.api.PodcastDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    savedStateHandle: SavedStateHandle,
) : LoadableViewModel<PodcastDetailDto>(UiState.Loading) {

    private val podcastId: String = savedStateHandle.toRoute<PodcastDetail>().podcastId

    init { load() }

    fun load() = load("Failed to load podcast") { podcastRepository.getPodcast(podcastId) }

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

    fun toggleListening(listening: Boolean) {
        viewModelScope.launch {
            try {
                podcastRepository.setListening(podcastId, listening)
                load()
            } catch (_: Exception) {}
        }
    }

    fun removePodcast(onRemoved: () -> Unit) {
        viewModelScope.launch {
            try {
                podcastRepository.removePodcast(podcastId)
                onRemoved()
            } catch (_: Exception) {}
        }
    }

    fun togglePlayed(episodeId: String, newPlayed: Boolean) {
        viewModelScope.launch {
            try {
                episodeRepository.setPlayed(episodeId, newPlayed)
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
