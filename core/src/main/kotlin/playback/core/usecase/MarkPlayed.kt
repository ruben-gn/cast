package playback.core.usecase

import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId

class MarkPlayed(
    private val persistence: PlaybackPersistence,
) {
    suspend operator fun invoke(episodeId: EpisodeId) {
        persistence.markPlayed(episodeId)
    }
}
