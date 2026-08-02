package cast.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cast.android.ui.UiState
import cast.android.ui.components.EpisodeItem
import cast.android.ui.components.RecentScreenSkeleton
import cast.android.ui.components.SeriesStackRow
import cast.android.ui.nav.EpisodeDetail
import cast.android.ui.nav.PodcastDetail
import cast.android.ui.viewmodel.DownloadsViewModel
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.ui.viewmodel.RecentRow
import cast.android.ui.viewmodel.RecentViewModel
import cast.android.ui.viewmodel.groupIntoRows
import cast.android.ui.viewmodel.guessSeriesName
import cast.api.EpisodeDetailDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(navController: NavHostController) {
    val vm: RecentViewModel = hiltViewModel()
    val downloadsVm: DownloadsViewModel = hiltViewModel()
    val playerVm = LocalPlayerViewModel.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val queueIds by vm.queueIds.collectAsStateWithLifecycle()
    val expandedSeries by vm.expandedSeries.collectAsStateWithLifecycle()
    val downloadStatuses by downloadsVm.statuses.collectAsStateWithLifecycle()
    val downloadProgress by downloadsVm.progress.collectAsStateWithLifecycle()

    var groupTarget by remember { mutableStateOf<EpisodeDetailDto?>(null) }
    var ungroupTarget by remember { mutableStateOf<RecentRow.Series?>(null) }

    LaunchedEffect(playerVm) {
        playerVm.episodeCompleted.collect { episodeId -> vm.onEpisodeCompleted(episodeId) }
    }

    // The server already drops played episodes from "recent", but the in-the-moment removal above
    // only fires while this screen is composed. An episode finished from Now Playing or with the app
    // backgrounded misses it, so refetch whenever the screen resumes (foreground or tab switch).
    // load() keeps the current list visible while it refetches, so returning here never flashes the skeleton.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }

    @Composable
    fun episodeRow(episode: EpisodeDetailDto) {
        EpisodeItem(
            episode = episode,
            onPlay = { playerVm.playEpisode(episode) },
            downloadStatus = downloadStatuses[episode.id],
            downloadProgress = downloadProgress[episode.id] ?: 0f,
            onDownloadAction = { downloadsVm.toggle(episode) },
            onTogglePlayed = { newPlayed -> vm.togglePlayed(episode.id, newPlayed) },
            onAddToQueue = { vm.addToQueue(episode.id) },
            onClick = { navController.navigate(EpisodeDetail(episode.id)) },
            onGoToPodcast = episode.podcastId?.let { id ->
                { navController.navigate(PodcastDetail(id)) }
            },
            onGroupSeries = if (episode.podcastId != null && episode.seriesName == null) {
                { groupTarget = episode }
            } else null,
            queuePosition = queueIds.indexOf(episode.id).takeIf { it >= 0 }?.plus(1),
        )
        HorizontalDivider()
    }

    PullToRefreshBox(
        isRefreshing = uiState is UiState.Loading,
        onRefresh = { vm.load() },
        modifier = Modifier.fillMaxSize(),
    ) {
        when (val state = uiState) {
            is UiState.Loading -> RecentScreenSkeleton()
            is UiState.Error -> Box(Modifier.fillMaxSize()) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
            is UiState.Success -> {
                val rows = groupIntoRows(state.data)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(rows, key = { row -> when (row) {
                        is RecentRow.Single -> row.episode.id
                        is RecentRow.Series -> row.key
                    } }) { row ->
                        when (row) {
                            is RecentRow.Single -> episodeRow(row.episode)
                            is RecentRow.Series -> Column(modifier = Modifier.fillMaxWidth()) {
                                SeriesStackRow(
                                    episodes = row.episodes,
                                    expanded = row.key in expandedSeries,
                                    onToggle = { vm.toggleSeries(row.key) },
                                    onUngroup = { ungroupTarget = row },
                                )
                                if (row.key in expandedSeries) {
                                    row.episodes.forEach { episode -> episodeRow(episode) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    groupTarget?.let { target ->
        var text by remember(target.id) {
            mutableStateOf(
                guessSeriesName(
                    title = target.title,
                    siblingTitles = (uiState as? UiState.Success)?.data
                        .orEmpty()
                        .filter { it.podcastId == target.podcastId && it.id != target.id }
                        .map { it.title },
                )
            )
        }
        AlertDialog(
            onDismissRequest = { groupTarget = null },
            title = { Text("Group series") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.groupSeries(target.podcastId!!, text)
                    groupTarget = null
                }) { Text("Group") }
            },
            dismissButton = {
                TextButton(onClick = { groupTarget = null }) { Text("Cancel") }
            },
        )
    }

    ungroupTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { ungroupTarget = null },
            title = { Text("Ungroup '${target.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    vm.ungroupSeries(target.episodes.first().podcastId!!, target.name)
                    ungroupTarget = null
                }) { Text("Ungroup") }
            },
            dismissButton = {
                TextButton(onClick = { ungroupTarget = null }) { Text("Cancel") }
            },
        )
    }
}
