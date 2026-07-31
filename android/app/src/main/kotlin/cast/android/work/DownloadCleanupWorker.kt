package cast.android.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cast.android.domain.repository.DownloadRepository
import cast.android.domain.repository.impl.DownloadTimestampStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** Removes downloads that have neither been re-downloaded nor played in [MAX_AGE_MS]. */
@HiltWorker
class DownloadCleanupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val downloadRepository: DownloadRepository,
    private val timestampStore: DownloadTimestampStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val now = System.currentTimeMillis()
        var removed = 0
        downloadRepository.downloadedEpisodes().forEach { episode ->
            val lastTouchedAt = timestampStore.lastTouchedAt(episode.id)
            if (lastTouchedAt == null || now - lastTouchedAt > MAX_AGE_MS) {
                downloadRepository.remove(episode.id)
                removed++
            }
        }
        removed
    }.fold(
        onSuccess = { count ->
            Log.d(TAG, "cleanup ok: removed $count untouched download(s)")
            Result.success()
        },
        onFailure = { e ->
            Log.w(TAG, "cleanup failed, will retry", e)
            Result.retry()
        },
    )

    companion object {
        const val WORK_NAME = "download_cleanup"
        private const val TAG = "DownloadCleanupWorker"
        private val MAX_AGE_MS = TimeUnit.DAYS.toMillis(14)
    }
}
