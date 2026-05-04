package playback.core.models

import shared.model.EpisodeId
import java.time.Instant

data class PlaybackState(
    val episodeId: EpisodeId,
    val progressMs: Long,
    val updatedAt: Instant,
    val played: Boolean = false,
)