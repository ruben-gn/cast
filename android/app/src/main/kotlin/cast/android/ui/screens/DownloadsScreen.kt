package cast.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import cast.android.ui.nav.EpisodeDetail
import cast.android.ui.viewmodel.DownloadsViewModel
import cast.android.ui.viewmodel.LocalPlayerViewModel

@Composable
fun DownloadsScreen(navController: NavHostController) {
    val vm: DownloadsViewModel = hiltViewModel()
    val playerVm = LocalPlayerViewModel.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is UiState.Loading -> Box(Modifier.fillMaxSize()) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
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
            val episodes = state.data
            if (episodes.isEmpty()) {
                Box(Modifier.fillMaxSize()) {
                    Text("Nothing downloaded yet", Modifier.align(Alignment.Center))
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(episodes, key = { it.id }) { episode ->
                        EpisodeItem(
                            episode = episode,
                            onPlay = { playerVm.playEpisode(episode) },
                            onClick = { navController.navigate(EpisodeDetail(episode.id)) },
                            downloadStatus = statuses[episode.id],
                            onDownloadAction = { vm.toggle(episode) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
