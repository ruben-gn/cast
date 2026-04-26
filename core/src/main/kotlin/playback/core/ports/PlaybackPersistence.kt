package playback.core.ports

import playback.core.models.PlaybackState
import shared.model.EpisodeId

interface PlaybackPersistence {
    suspend fun update(playbackState: PlaybackState)
    suspend fun get(episodeId: EpisodeId): PlaybackState?
}