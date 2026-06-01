package cast.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cast.android.ui.viewmodel.LocalPlayerViewModel
import cast.android.util.relativeTime
import cast.api.EpisodeDetailDto
import coil3.compose.AsyncImage

@Composable
fun EpisodeItem(
    episode: EpisodeDetailDto,
    onPlay: () -> Unit,
    onTogglePlayed: ((Boolean) -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val playerVm = LocalPlayerViewModel.current
    val currentMediaItem by playerVm.currentMediaItem.collectAsStateWithLifecycle()
    val isPlaying by playerVm.isPlaying.collectAsStateWithLifecycle()
    val position by playerVm.position.collectAsStateWithLifecycle()
    val duration by playerVm.duration.collectAsStateWithLifecycle()

    val lastKnownProgress by playerVm.lastKnownProgress.collectAsStateWithLifecycle()

    val isCurrent = currentMediaItem?.mediaId == episode.id

    var played by remember(episode.id, episode.played) { mutableStateOf(episode.played) }

    val savedProgress: Float? = when {
        played -> null
        !isCurrent && lastKnownProgress[episode.id]?.second.let { it != null && it > 0 } -> {
            val (pos, dur) = lastKnownProgress[episode.id]!!
            pos.toFloat() / dur.toFloat()
        }
        episode.progressMs > 0 && (episode.durationMs ?: 0L) > 0L ->
            episode.progressMs.toFloat() / episode.durationMs!!.toFloat()
        else -> null
    }

    val progress = if (isCurrent && duration > 0) position.toFloat() / duration.toFloat()
                   else if (isCurrent) savedProgress ?: 0f
                   else 0f

    val staticProgress = if (!isCurrent) savedProgress else null

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            episode.podcastImage?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(episode.podcastName, relativeTime(episode.publishedAt), episode.duration)
                    .joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onTogglePlayed != null) {
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
                    }
                    if (onAddToQueue != null) {
                        IconButton(onClick = onAddToQueue) {
                            Icon(
                                imageVector = Icons.Default.PlaylistAdd,
                                contentDescription = "Add to queue",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onPlay) {
                        Icon(
                            imageVector = if (isCurrent && isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isCurrent && isPlaying) "Pause" else "Play",
                        )
                    }
                }
            }
        }
        when {
            isCurrent -> LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
            staticProgress != null -> LinearProgressIndicator(
                progress = { staticProgress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
            )
        }
    }
}
