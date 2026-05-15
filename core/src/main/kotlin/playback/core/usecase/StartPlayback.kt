package playback.core.usecase

import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.time.Clock

class StartPlayback(
    private val clock: Clock,
    private val state: PlaybackPersistence,
) {
    suspend operator fun invoke(episodeId: EpisodeId, startPositionMs: Long) {
        state.resetProgress(episodeId, startPositionMs, clock.instant())
    }
}
