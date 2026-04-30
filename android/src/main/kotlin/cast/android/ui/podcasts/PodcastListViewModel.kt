package cast.android.ui.podcasts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cast.android.data.PodcastRepository
import cast.android.ui.UiState
import cast.api.PodcastSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastListViewModel @Inject constructor(
    private val repository: PodcastRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<PodcastSummaryDto>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PodcastSummaryDto>>> = _uiState.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _uiState.value = try {
                UiState.Success(repository.listPodcasts())
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load podcasts")
            }
        }
    }
}
