package playback.fakes

import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.time.Instant

class FakePlaybackPersistence : PlaybackPersistence {
    private val storage = mutableMapOf<EpisodeId, PlaybackState>()

    override suspend fun updateProgress(episodeId: EpisodeId, progressMs: Long, updatedAt: Instant) {
        storage[episodeId] = storage.getOrElse(episodeId) {
            PlaybackState(episodeId, progressMs, updatedAt, played = false)
        }.copy(progressMs = progressMs, updatedAt = updatedAt)
    }

    override suspend fun markPlayed(episodeId: EpisodeId) {
        storage[episodeId] = storage.getOrElse(episodeId) {
            PlaybackState(episodeId, 0, java.time.Instant.now(), played = false)
        }.copy(played = true)
    }

    override suspend fun get(episodeId: EpisodeId): PlaybackState? {
        return storage[episodeId]
    }
}