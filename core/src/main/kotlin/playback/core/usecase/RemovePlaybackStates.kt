package playback.core.usecase

import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId

class RemovePlaybackStates(
    private val persistence: PlaybackPersistence,
) {
    suspend operator fun invoke(episodeIds: List<EpisodeId>) {
        persistence.removeAll(episodeIds)
    }
}
