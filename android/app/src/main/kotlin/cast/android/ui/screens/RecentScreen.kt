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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cast.android.ui.UiState
import cast.android.ui.components.EpisodeItem
import cast.android.ui.components.RecentScreenSkeleton
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.ui.viewmodel.RecentViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentScreen(navController: NavHostController) {
    val vm: RecentViewModel = hiltViewModel()
    val playerVm = LocalPlayerViewModel.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()

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
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
