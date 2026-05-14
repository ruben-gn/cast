package playback.core.ports

import playback.core.models.PlaybackState
import shared.model.EpisodeId
import java.time.Instant

interface PlaybackPersistence {
    // Creates the row if absent (played = false); on conflict updates progress fields only — never touches played.
    suspend fun updateProgress(episodeId: EpisodeId, progressMs: Long, updatedAt: Instant)
    suspend fun markPlayed(episodeId: EpisodeId)
    suspend fun get(episodeId: EpisodeId): PlaybackState?
    suspend fun getAll(ids: List<EpisodeId>): Map<EpisodeId, PlaybackState>
}