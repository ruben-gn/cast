package cast.android.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import cast.android.domain.repository.PodcastRepository
import cast.android.ui.UiState
import cast.api.PodcastSummaryDto
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

enum class ViewMode { Grid, List }
enum class SortBy { Name, Updated }

@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val podcastRepository: PodcastRepository,
    @ApplicationContext private val context: Context,
) : LoadableViewModel<List<PodcastSummaryDto>>(
    podcastRepository.cachedPodcasts()?.let { UiState.Success(it) } ?: UiState.Loading
) {

    var showAddSheet by mutableStateOf(false)
        private set
    var isAdding by mutableStateOf(false)
        private set
    var addError: String? by mutableStateOf(null)
        private set
    var viewMode: ViewMode by mutableStateOf(ViewMode.Grid)
        private set
    var sortBy: SortBy by mutableStateOf(SortBy.Name)
        private set

    init { load() }

    fun load() = load("Failed to load podcasts") { podcastRepository.listPodcasts() }

    fun openAddSheet() { showAddSheet = true; addError = null }
    fun dismissAddSheet() { showAddSheet = false; addError = null }

    fun toggleViewMode() {
        viewMode = if (viewMode == ViewMode.Grid) ViewMode.List else ViewMode.Grid
    }

    fun selectSortBy(sort: SortBy) { sortBy = sort }

    val listeningPodcasts: List<PodcastSummaryDto>
        get() = (uiState.value as? UiState.Success)?.data
            .orEmpty()
            .filter { it.listening }
            .applySortBy()

    val notListeningPodcasts: List<PodcastSummaryDto>
        get() = (uiState.value as? UiState.Success)?.data
            .orEmpty()
            .filter { !it.listening }
            .applySortBy()

    private fun List<PodcastSummaryDto>.applySortBy() = when (sortBy) {
        SortBy.Name -> sortedBy { it.name.lowercase() }
        SortBy.Updated -> sortedByDescending { it.latestEpisodeAt }
    }

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

    fun toggleListening(podcastId: String, listening: Boolean) {
        viewModelScope.launch {
            try {
                podcastRepository.setListening(podcastId, listening)
                load()
            } catch (_: Exception) {}
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
