package cast.android.ui.podcasts

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import cast.android.data.PodcastRepository
import cast.android.navigation.PodcastDetail
import cast.android.ui.UiState
import cast.api.PodcastDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PodcastDetailViewModel @Inject constructor(
    private val repository: PodcastRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val podcastId = savedStateHandle.toRoute<PodcastDetail>().podcastId

    private val _uiState = MutableStateFlow<UiState<PodcastDetailDto>>(UiState.Loading)
    val uiState: StateFlow<UiState<PodcastDetailDto>> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.value = try {
                UiState.Success(repository.getPodcast(podcastId))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Failed to load podcast")
            }
        }
    }
}
