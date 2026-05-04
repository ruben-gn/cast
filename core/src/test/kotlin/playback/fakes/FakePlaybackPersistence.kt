package playback.fakes

import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId

class FakePlaybackPersistence : PlaybackPersistence {
    private val storage = mutableMapOf<EpisodeId, PlaybackState>()

    override suspend fun update(playbackState: PlaybackState) {
        storage[playbackState.episodeId] = playbackState
    }

    override suspend fun markPlayed(episodeId: EpisodeId) {
        storage[episodeId] = storage.getOrElse(episodeId) {
            PlaybackState(episodeId, 0, java.time.Instant.now())
        }.copy(played = true)
    }

    override suspend fun get(episodeId: EpisodeId): PlaybackState? {
        return storage[episodeId]
    }
}