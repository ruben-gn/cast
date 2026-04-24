package playback.core

import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.time.Clock

class GetPlaybackState(
    private val clock: Clock,
    private val persistence: PlaybackPersistence,
) {
    operator fun invoke(episodeId: String) =
        persistence.get(EpisodeId(episodeId))
            ?: PlaybackState(EpisodeId(episodeId), 0, clock.instant())
}

