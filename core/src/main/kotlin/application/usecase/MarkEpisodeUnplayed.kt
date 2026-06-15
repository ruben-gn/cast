package application.usecase

import playback.core.usecase.MarkUnplayed
import podcast.core.usecase.FindEpisode
import shared.model.EpisodeId

class MarkEpisodeUnplayed(
    private val findEpisode: FindEpisode,
    private val markUnplayed: MarkUnplayed,
) {
    suspend operator fun invoke(episodeId: EpisodeId): Boolean {
        findEpisode(episodeId) ?: return false
        markUnplayed(episodeId)
        return true
    }
}
