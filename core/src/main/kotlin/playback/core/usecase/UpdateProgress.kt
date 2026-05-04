package playback.core.usecase

import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.time.Clock

class UpdateProgress(
    private val clock: Clock,
    private val state: PlaybackPersistence,
) {
    suspend operator fun invoke(episodeId: String, progressMs: Long) {
        val playbackState = PlaybackState(EpisodeId(episodeId), progressMs, clock.instant())
        state.update(playbackState)
    }
}