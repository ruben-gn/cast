package cast.android.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import cast.android.ui.UiState
import cast.android.ui.components.ConfirmIconButton
import cast.android.ui.components.EpisodeDescription
import cast.android.ui.viewmodel.EpisodeDetailViewModel
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.util.relativeTime
import cast.api.EpisodeDetailDto
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(navController: NavController) {
    val vm: EpisodeDetailViewModel = hiltViewModel()
    val uiState by vm.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = (uiState as? UiState.Success)?.data?.title ?: ""
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val state = uiState) {
            is UiState.Loading -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is UiState.Error -> Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(state.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            is UiState.Success -> EpisodeDetailContent(
                episode = state.data,
                modifier = Modifier.padding(innerPadding),
                onAddToQueue = { vm.addToQueue() },
                onTogglePlayed = { vm.togglePlayed(it) },
            )
        }
    }
}

@Composable
private fun EpisodeDetailContent(
    episode: EpisodeDetailDto,
    modifier: Modifier = Modifier,
    onAddToQueue: () -> Unit,
    onTogglePlayed: (Boolean) -> Unit,
) {
    val playerVm = LocalPlayerViewModel.current
    val currentMediaItem by playerVm.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying by playerVm.isPlaying.collectAsStateWithLifecycle()
    val position by playerVm.position.collectAsStateWithLifecycle()
    val duration by playerVm.duration.collectAsStateWithLifecycle()

    val isCurrent = currentMediaItem?.mediaId == episode.id
    val liveProgress = if (isCurrent && duration > 0) position.toFloat() / duration.toFloat() else 0f
    val staticProgress = if (!isCurrent && episode.progressMs > 0 && (episode.durationMs ?: 0L) > 0L)
        episode.progressMs.toFloat() / episode.durationMs!!.toFloat() else null

    var played by remember(episode.id, episode.played) { mutableStateOf(episode.played) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        episode.podcastImage?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .align(Alignment.CenterHorizontally),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(episode.title, style = MaterialTheme.typography.titleLarge)
            val sub = listOfNotNull(episode.podcastName, relativeTime(episode.publishedAt), episode.duration)
                .joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalIconButton(
                onClick = { playerVm.playEpisode(episode) },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(28.dp),
                )
            }
            ConfirmIconButton(
                icon = Icons.Default.PlaylistAdd,
                contentDescription = "Add to queue",
                onClick = onAddToQueue,
            )
            IconButton(onClick = {
                val newPlayed = !played
                played = newPlayed
                onTogglePlayed(newPlayed)
            }) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = if (played) "Mark as unplayed" else "Mark as played",
                    tint = if (played) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                )
            }
            Spacer(Modifier.width(0.dp))
        }

        when {
            isCurrent -> LinearProgressIndicator(
                progress = { liveProgress },
                modifier = Modifier.fillMaxWidth(),
            )
            staticProgress != null -> LinearProgressIndicator(
                progress = { staticProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
            )
        }

        if (episode.description.isNotBlank()) {
            EpisodeDescription(html = episode.description)
        }
    }
}

