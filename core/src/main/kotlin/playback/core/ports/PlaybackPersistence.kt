package playback.core.ports

import playback.core.models.PlaybackState
import shared.model.EpisodeId

interface PlaybackPersistence {
    fun update(playbackState: PlaybackState)
    fun get(episodeId: EpisodeId): PlaybackState?
}