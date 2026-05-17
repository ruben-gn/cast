package cast.android.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cast.android.domain.repository.PodcastRepository
import cast.android.ui.UiState
import cast.api.PodcastSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<PodcastSummaryDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PodcastSummaryDto>>> = _uiState.asStateFlow()

    var showAddSheet by mutableStateOf(false)
        private set
    var isAdding by mutableStateOf(false)
        private set
    var addError: String? by mutableStateOf(null)
        private set

    init { load() }

    fun load() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(podcastRepository.listPodcasts())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load podcasts")
            }
        }
    }

    fun openAddSheet() { showAddSheet = true; addError = null }
    fun dismissAddSheet() { showAddSheet = false; addError = null }

    fun addPodcast(feedUrl: String) {
        viewModelScope.launch {
            isAdding = true
            addError = null
            try {
                podcastRepository.addPodcast(feedUrl)
                showAddSheet = false
                load()
            } catch (e: Exception) {
                addError = e.message ?: "Failed to add podcast"
            } finally {
                isAdding = false
            }
        }
    }
}
