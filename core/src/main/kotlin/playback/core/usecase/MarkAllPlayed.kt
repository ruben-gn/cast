package playback.core.usecase

import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId

class MarkAllPlayed(private val persistence: PlaybackPersistence) {
    suspend operator fun invoke(episodeIds: List<EpisodeId>) {
        persistence.markAllPlayed(episodeIds)
    }
}
