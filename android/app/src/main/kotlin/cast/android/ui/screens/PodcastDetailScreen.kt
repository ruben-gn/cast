package cast.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cast.android.ui.UiState
import cast.android.ui.components.EpisodeItem
import cast.android.ui.nav.EpisodeDetail
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.ui.viewmodel.PodcastDetailViewModel
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastDetailScreen(podcastId: String, navController: NavHostController) {
    val vm: PodcastDetailViewModel = hiltViewModel()
    val playerVm = LocalPlayerViewModel.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val queueIds by vm.queueIds.collectAsStateWithLifecycle()

    var menuExpanded by remember { mutableStateOf(false) }
    var showRemoveDialog by remember { mutableStateOf(false) }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove podcast?") },
            text = { Text("This removes the podcast along with its playback progress and any queued episodes.") },
            confirmButton = {
                TextButton(onClick = {
                    showRemoveDialog = false
                    vm.removePodcast { navController.popBackStack() }
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val name = (uiState as? UiState.Success)?.data?.name ?: ""
                    Text(name)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState is UiState.Success) {
                        val podcast = (uiState as UiState.Success).data
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Mark all played") },
                                onClick = {
                                    menuExpanded = false
                                    vm.markAllPlayed()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(if (podcast.listening) "Stop listening" else "Start listening") },
                                onClick = {
                                    menuExpanded = false
                                    vm.toggleListening(!podcast.listening)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove podcast") },
                                onClick = {
                                    menuExpanded = false
                                    showRemoveDialog = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Loading -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            is UiState.Error -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(16.dp),
                )
            }
            is UiState.Success -> {
                val podcast = state.data
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = innerPadding,
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            AsyncImage(
                                model = podcast.image,
                                contentDescription = podcast.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                            ) {
                                Text(podcast.name, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        HorizontalDivider()
                    }
                    items(podcast.episodes, key = { it.id }) { episode ->
                        EpisodeItem(
                            episode = episode,
                            onPlay = { playerVm.playEpisode(episode) },
                            onTogglePlayed = { newPlayed -> vm.togglePlayed(episode.id, newPlayed) },
                            onAddToQueue = { vm.addToQueue(episode.id) },
                            onClick = { navController.navigate(EpisodeDetail(episode.id)) },
                            queuePosition = queueIds.indexOf(episode.id).takeIf { it >= 0 }?.plus(1),
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
