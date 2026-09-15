package application.usecase

import playback.core.usecase.GetPlaybackState
import playback.core.usecase.MarkPlayed
import playback.core.usecase.UpdateProgress
import podcast.core.usecase.FindEpisode
import shared.model.EpisodeId
import java.time.Instant
import kotlin.time.Duration

/**
 * Records playback progress and promotes an episode to played once it is all but over.
 *
 * Clients only report `ended` on an exact end-of-stream, which real listening rarely reaches — a
 * skipped outro or a stalled buffer leaves an episode a minute short, so it never left Recent.
 * Deciding it here rather than in each player keeps every client (app, webapp, Auto) in agreement.
 */
class RecordProgress(
    private val findEpisode: FindEpisode,
    private val getPlaybackState: GetPlaybackState,
    private val updateProgress: UpdateProgress,
    private val markPlayed: MarkPlayed,
) {
    suspend operator fun invoke(episodeId: EpisodeId, progressMs: Long, updatedAt: Instant?) {
        val before = getPlaybackState(episodeId).progressMs
        updateProgress(episodeId, progressMs, updatedAt)
        // Only a report that carries the episode past where it already stood can finish it. A
        // client replaying a stale position must never undo a deliberate "mark unplayed".
        if (progressMs <= before) return
        val duration = findEpisode(episodeId)?.duration ?: return
        if (duration <= Duration.ZERO) return
        if (progressMs >= duration.inWholeMilliseconds * FINISHED_FRACTION) markPlayed(episodeId)
    }

    private companion object {
        // A fraction rather than a fixed tail: it scales with episode length, and at 97% it still
        // catches the observed case of playback stopping ~1 minute into a 47-minute episode's outro.
        const val FINISHED_FRACTION = 0.97
    }
}
