package playback.core

import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.time.Clock

class UpdatePlaybackState(
    val clock: Clock,
    val state: PlaybackPersistence,
) {
    operator fun invoke(episodeId: String, progressMs: Long) =
        PlaybackState(EpisodeId(episodeId), progressMs, clock.instant())
            .let(state::update)
}