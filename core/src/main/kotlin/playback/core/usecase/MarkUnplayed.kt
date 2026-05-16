package playback.core.usecase

import playback.core.ports.PlaybackPersistence
import shared.model.EpisodeId

class MarkUnplayed(
    private val persistence: PlaybackPersistence,
) {
    suspend operator fun invoke(episodeId: EpisodeId) {
        persistence.markUnplayed(episodeId)
    }
}
