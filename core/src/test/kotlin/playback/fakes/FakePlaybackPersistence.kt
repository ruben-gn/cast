package playback.fakes

import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId

class FakePlaybackPersistence : PlaybackPersistence {
    private val storage = mutableMapOf<EpisodeId, PlaybackState>()

    override fun update(playbackState: PlaybackState) {
        storage[playbackState.episodeId] = playbackState
    }

    override fun get(episodeId: EpisodeId): PlaybackState? {
        return storage[episodeId]
    }
}