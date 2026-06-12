package cast.android.ui.viewmodel

import androidx.lifecycle.viewModelScope
import cast.android.domain.repository.QueueRepository
import cast.android.ui.UiState
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val queueRepository: QueueRepository,
) : LoadableViewModel<List<EpisodeDetailDto>>(
    queueRepository.cachedQueue()?.let { UiState.Success(it) } ?: UiState.Loading
) {

    private var pendingReorderJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            queueRepository.queueIds.collect { _ ->
                val cached = queueRepository.cachedQueue()
                if (cached != null) _uiState.value = UiState.Success(cached)
            }
        }
    }

    fun load() = load("Failed to load queue") { queueRepository.getQueue() }

    fun removeFromQueue(episodeId: String) {
        viewModelScope.launch {
            try {
                val updated = queueRepository.removeFromQueue(episodeId)
                _uiState.value = UiState.Success(updated)
            } catch (_: Exception) {
                val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
                _uiState.value = UiState.Success(current.filterNot { it.id == episodeId })
            }
        }
    }

    fun reorder(fromIndex: Int, toIndex: Int) {
        val current = (_uiState.value as? UiState.Success)?.data ?: return
        val mutable = current.toMutableList()
        mutable.add(toIndex, mutable.removeAt(fromIndex))
        _uiState.value = UiState.Success(mutable)

        val newOrder = mutable.map { it.id }
        pendingReorderJob?.cancel()
        pendingReorderJob = viewModelScope.launch {
            delay(300)
            try {
                queueRepository.reorderQueue(newOrder)
            } catch (_: Exception) {
                load()
            }
        }
    }
}
