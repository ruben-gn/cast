package playback.core.usecase

import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.time.Clock

class UpdateProgress(
    private val clock: Clock,
    private val state: PlaybackPersistence,
) {
    suspend operator fun invoke(episodeId: EpisodeId, progressMs: Long) {
        state.updateProgress(episodeId, progressMs, clock.instant())
    }
}