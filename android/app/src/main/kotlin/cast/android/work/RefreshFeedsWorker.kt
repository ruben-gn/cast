package cast.android.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.PodcastRepository
import cast.android.notifications.NewEpisodeNotifier
import cast.android.notifications.NewEpisodeTracker
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RefreshFeedsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
    private val tracker: NewEpisodeTracker,
    private val notifier: NewEpisodeNotifier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val podcasts = podcastRepository.listPodcasts()
        val episodes = episodeRepository.getRecentEpisodes()
        val newEpisodes = tracker.newEpisodes(podcasts, episodes)
        notifier.notify(newEpisodes)
        tracker.advance(episodes)
        newEpisodes.size
    }.fold(
        onSuccess = { count ->
            Log.d(TAG, "refresh ok: notified $count new episode(s)")
            Result.success()
        },
        onFailure = { e ->
            Log.w(TAG, "refresh failed, will retry", e)
            Result.retry()
        },
    )

    companion object {
        const val WORK_NAME = "refresh_feeds"
        private const val TAG = "RefreshFeedsWorker"
    }
}
