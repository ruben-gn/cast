package cast.android.ui.viewmodel

import androidx.lifecycle.viewModelScope
import cast.android.domain.repository.DownloadRepository
import cast.android.domain.repository.DownloadStatus
import cast.android.ui.UiState
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : LoadableViewModel<List<EpisodeDetailDto>>(UiState.Loading) {

    val statuses: StateFlow<Map<String, DownloadStatus>> = downloadRepository.statuses
    val progress: StateFlow<Map<String, Float>> = downloadRepository.progress

    init {
        // StateFlow replays the current value, so this also performs the initial load.
        viewModelScope.launch {
            downloadRepository.statuses.collect { load() }
        }
    }

    fun load() = load("Failed to load downloads") { downloadRepository.downloadedEpisodes() }

    /** Not downloaded → start; downloading → cancel; downloaded → remove. */
    fun toggle(episode: EpisodeDetailDto) {
        when (statuses.value[episode.id]) {
            null -> downloadRepository.download(episode)
            DownloadStatus.DOWNLOADING, DownloadStatus.DOWNLOADED ->
                downloadRepository.remove(episode.id)
        }
    }
}
