package playback.core.usecase

import playback.core.models.PlaybackState
import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId

class GetPlaybackStates(private val persistence: PlaybackPersistence) {
    suspend operator fun invoke(ids: List<EpisodeId>): Map<EpisodeId, PlaybackState> =
        persistence.getAll(ids)
}
