package cast.android.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import cast.android.domain.repository.DownloadStatus
import cast.android.ui.UiState
import cast.android.ui.components.downloadSheetAction
import cast.android.ui.viewmodel.DownloadsViewModel
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.ui.viewmodel.QueueViewModel
import cast.api.EpisodeDetailDto
import coil3.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(navController: NavHostController) {
    val vm: QueueViewModel = hiltViewModel()
    val downloadsVm: DownloadsViewModel = hiltViewModel()
    val playerVm = LocalPlayerViewModel.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val downloadStatuses by downloadsVm.statuses.collectAsStateWithLifecycle()

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
                    Text("Your queue is empty", Modifier.align(Alignment.Center))
                }
            } else {
                val listState = rememberLazyListState()
                val reorderState = rememberReorderableLazyListState(listState) { from, to ->
                    vm.reorder(from.index, to.index)
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(episodes, key = { it.id }) { episode ->
                        ReorderableItem(reorderState, key = episode.id) { _ ->
                            var showSheet by remember(episode.id) { mutableStateOf(false) }
                            QueueEpisodeRow(
                                episode = episode,
                                onLongClick = { showSheet = true },
                                dragHandleModifier = Modifier.draggableHandle(),
                            )
                            HorizontalDivider()
                            if (showSheet) {
                                QueueEpisodeActionsSheet(
                                    episodeTitle = episode.title,
                                    downloadStatus = downloadStatuses[episode.id],
                                    onPlay = { playerVm.playEpisode(episode) },
                                    onRemove = { vm.removeFromQueue(episode.id) },
                                    onDownloadAction = { downloadsVm.toggle(episode) },
                                    onDismiss = { showSheet = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueEpisodeRow(
    episode: EpisodeDetailDto,
    onLongClick: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        episode.podcastImage?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val sub = listOfNotNull(episode.podcastName, episode.duration).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        androidx.compose.material3.IconButton(modifier = dragHandleModifier, onClick = {}) {
            Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueEpisodeActionsSheet(
    episodeTitle: String,
    downloadStatus: DownloadStatus?,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onDownloadAction: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = episodeTitle,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        QueueSheetAction(Icons.Default.PlayArrow, "Play now") { onPlay(); onDismiss() }
        val (downloadIcon, downloadLabel) = downloadSheetAction(downloadStatus)
        QueueSheetAction(downloadIcon, downloadLabel) { onDownloadAction(); onDismiss() }
        QueueSheetAction(Icons.Default.Delete, "Remove from queue") { onRemove(); onDismiss() }
    }
}

@Composable
private fun QueueSheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
