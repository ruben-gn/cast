package cast.android.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    viewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val controller by viewModel.controller.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val mediaItem by viewModel.currentMediaItem.collectAsStateWithLifecycle()

    var position by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableLongStateOf(0L) }

    val durationMs = remember(mediaItem) {
        mediaItem?.mediaMetadata?.extras?.getLong("durationMs", 0L) ?: 0L
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            if (!isDragging) position = controller?.currentPosition ?: 0L
            delay(200)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = mediaItem?.mediaMetadata?.artworkUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = mediaItem?.mediaMetadata?.title?.toString() ?: "",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
            )

            Spacer(Modifier.height(16.dp))

            Slider(
                value = (if (isDragging) dragPosition else position).toFloat(),
                valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
                onValueChange = {
                    isDragging = true
                    dragPosition = it.toLong()
                },
                onValueChangeFinished = {
                    controller?.seekTo(dragPosition)
                    position = dragPosition
                    isDragging = false
                },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(formatMs(if (isDragging) dragPosition else position), style = MaterialTheme.typography.bodySmall)
                Text(formatMs(durationMs), style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { controller?.seekTo((controller?.currentPosition ?: 0L) - 15_000) },
                    modifier = Modifier.size(56.dp),
                ) {
                    Text("−15s", style = MaterialTheme.typography.labelLarge)
                }

                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                    IconButton(
                        onClick = { if (isPlaying) controller?.pause() else controller?.play() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(
                            text = if (isPlaying) "⏸" else "▶",
                            style = MaterialTheme.typography.headlineLarge,
                        )
                    }
                }

                IconButton(
                    onClick = { controller?.seekTo((controller?.currentPosition ?: 0L) + 30_000) },
                    modifier = Modifier.size(56.dp),
                ) {
                    Text("+30s", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
