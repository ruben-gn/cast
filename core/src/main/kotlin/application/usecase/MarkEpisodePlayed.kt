package application.usecase

import playback.core.usecase.MarkPlayed
import podcast.core.usecase.FindEpisode
import shared.model.EpisodeId

class MarkEpisodePlayed(
    private val findEpisode: FindEpisode,
    private val markPlayed: MarkPlayed,
) {
    suspend operator fun invoke(episodeId: EpisodeId): Boolean {
        findEpisode(episodeId) ?: return false
        markPlayed(episodeId)
        return true
    }
}
