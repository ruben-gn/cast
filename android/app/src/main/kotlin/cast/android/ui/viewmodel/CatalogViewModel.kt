package cast.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cast.android.domain.repository.PodcastRepository
import cast.android.ui.UiState
import cast.api.PodcastSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    @ApplicationContext private val context: Context,
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

    fun importOpml(uri: Uri) {
        viewModelScope.launch {
            isAdding = true
            addError = null
            try {
                val bytes = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.readBytes()
                        ?: throw Exception("Could not read file")
                }
                val requestBody = bytes.toRequestBody("application/octet-stream".toMediaType())
                val part = MultipartBody.Part.createFormData("opml", "import.opml", requestBody)
                podcastRepository.importOpml(part)
                showAddSheet = false
                load()
            } catch (e: Exception) {
                addError = e.message ?: "Failed to import OPML"
            } finally {
                isAdding = false
            }
        }
    }
}
