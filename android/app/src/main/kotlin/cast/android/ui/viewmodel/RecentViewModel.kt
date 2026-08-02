package cast.android.ui.viewmodel

import androidx.lifecycle.viewModelScope
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.PodcastRepository
import cast.android.domain.repository.QueueRepository
import cast.android.ui.UiState
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RecentViewModel @Inject constructor(
    private val episodeRepository: EpisodeRepository,
    private val queueRepository: QueueRepository,
    private val podcastRepository: PodcastRepository,
) : LoadableViewModel<List<EpisodeDetailDto>>(
    episodeRepository.cachedRecentEpisodes()?.let { UiState.Success(it) } ?: UiState.Loading
) {

    val queueIds: StateFlow<List<String>> = queueRepository.queueIds

    private val _expandedSeries = MutableStateFlow<Set<String>>(emptySet())
    val expandedSeries: StateFlow<Set<String>> = _expandedSeries

    init { load() }

    fun toggleSeries(key: String) {
        _expandedSeries.value =
            if (key in _expandedSeries.value) _expandedSeries.value - key
            else _expandedSeries.value + key
    }

    fun groupSeries(podcastId: String, name: String) {
        viewModelScope.launch {
            try {
                podcastRepository.createSeriesRule(podcastId, name)
                load()
            } catch (_: Exception) {}
        }
    }

    fun ungroupSeries(podcastId: String, name: String) {
        viewModelScope.launch {
            try {
                podcastRepository.deleteSeriesRule(podcastId, name)
                load()
            } catch (_: Exception) {}
        }
    }

    fun load() = load("Failed to load episodes") { episodeRepository.getRecentEpisodes() }

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
                episodeRepository.setPlayed(episodeId, newPlayed)
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
