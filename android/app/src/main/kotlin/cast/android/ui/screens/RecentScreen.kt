package cast.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import cast.android.ui.nav.EpisodeDetail
import cast.android.ui.nav.PodcastDetail
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.ui.viewmodel.RecentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(navController: NavHostController) {
    val vm: RecentViewModel = hiltViewModel()
    val playerVm = LocalPlayerViewModel.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(playerVm) {
        playerVm.episodeCompleted.collect { episodeId -> vm.onEpisodeCompleted(episodeId) }
    }

    // The server already drops played episodes from "recent", but the in-the-moment removal above
    // only fires while this screen is composed. An episode finished from Now Playing or with the app
    // backgrounded misses it, so refetch whenever the screen resumes (foreground or tab switch).
    // load() keeps the current list visible while it refetches, so returning here never flashes the skeleton.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.load() }

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
            is UiState.Success -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(state.data, key = { it.id }) { episode ->
                    EpisodeItem(
                        episode = episode,
                        onPlay = { playerVm.playEpisode(episode) },
                        onTogglePlayed = { newPlayed -> vm.togglePlayed(episode.id, newPlayed) },
                        onAddToQueue = { vm.addToQueue(episode.id) },
                        onClick = { navController.navigate(EpisodeDetail(episode.id)) },
                        onGoToPodcast = episode.podcastId?.let { id ->
                            { navController.navigate(PodcastDetail(id)) }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
