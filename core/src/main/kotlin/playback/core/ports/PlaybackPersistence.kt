package playback.core.ports

import playback.core.models.PlaybackState
import shared.model.EpisodeId

interface PlaybackPersistence {
    // Must not overwrite `played` if already true — a late progress update must not undo a markPlayed() that arrived first.
    suspend fun update(playbackState: PlaybackState)
    suspend fun markPlayed(episodeId: EpisodeId)
    suspend fun get(episodeId: EpisodeId): PlaybackState?
}