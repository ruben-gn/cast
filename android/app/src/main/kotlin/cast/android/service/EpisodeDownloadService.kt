package cast.android.service

import android.app.Notification
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import cast.android.R
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@OptIn(UnstableApi::class)
@AndroidEntryPoint
class EpisodeDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_notification_channel,
    0,
) {

    @Inject lateinit var episodeDownloadManager: DownloadManager

    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun getDownloadManager(): DownloadManager = episodeDownloadManager

    // No scheduler: an interrupted download resumes the next time the app (and thus the
    // DownloadManager) starts, which is good enough for user-initiated episode downloads.
    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification = notificationHelper.buildProgressNotification(
        this,
        R.drawable.ic_notification_episode,
        /* contentIntent = */ null,
        /* message = */ null,
        downloads,
        notMetRequirements,
    )

    companion object {
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "downloads"
    }
}
