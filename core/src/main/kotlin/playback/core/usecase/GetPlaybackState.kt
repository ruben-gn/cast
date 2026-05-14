package playback.core.usecase

import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId
import java.time.Clock

class GetPlaybackState(
    private val clock: Clock,
    private val persistence: PlaybackPersistence,
) {
    suspend operator fun invoke(episodeId: EpisodeId) =
        persistence.get(episodeId)
            ?: PlaybackState(episodeId, 0, clock.instant(), played = false)
}

