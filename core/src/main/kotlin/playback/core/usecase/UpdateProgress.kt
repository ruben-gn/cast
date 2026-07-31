package playback.core.usecase

import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.time.Clock
import java.time.Instant

class UpdateProgress(
    private val clock: Clock,
    private val state: PlaybackPersistence,
) {
    suspend operator fun invoke(episodeId: EpisodeId, progressMs: Long, updatedAt: Instant?) {
        state.updateProgress(episodeId, progressMs, updatedAt ?: clock.instant())
    }
}