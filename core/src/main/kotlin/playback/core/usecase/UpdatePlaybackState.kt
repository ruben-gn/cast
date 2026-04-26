package playback.core.usecase

import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.time.Clock

class UpdatePlaybackState(
    val clock: Clock,
    val state: PlaybackPersistence,
) {
    suspend operator fun invoke(episodeId: String, progressMs: Long) {
        val playbackState = PlaybackState(EpisodeId(episodeId), progressMs, clock.instant())
        state.update(playbackState)
    }
}