package cast.android.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cast.android.domain.repository.EpisodeRepository
import cast.android.domain.repository.PodcastRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RefreshFeedsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val podcastRepository: PodcastRepository,
    private val episodeRepository: EpisodeRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        podcastRepository.listPodcasts()
        episodeRepository.getRecentEpisodes()
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { Result.retry() },
    )

    companion object {
        const val WORK_NAME = "refresh_feeds"
    }
}
