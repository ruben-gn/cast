package cast.android.ui.viewmodel

import cast.android.domain.repository.DownloadRepository
import cast.android.ui.UiState
import cast.api.EpisodeDetailDto
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class DownloadedEpisodesViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
) : LoadableViewModel<List<EpisodeDetailDto>>(UiState.Loading) {

    init { load() }

    fun load() = load("Failed to load downloaded episodes") { downloadRepository.downloadedEpisodes() }

    fun remove(episodeIds: Set<String>) {
        episodeIds.forEach { downloadRepository.remove(it) }
        load()
    }
}
