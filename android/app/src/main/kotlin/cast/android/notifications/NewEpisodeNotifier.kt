package cast.android.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import cast.android.R
import cast.android.ui.MainActivity
import cast.api.EpisodeDetailDto
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts one notification per new episode, collapsed under a single group summary. */
@Singleton
class NewEpisodeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // areNotificationsEnabled() covers the POST_NOTIFICATIONS runtime grant, which lint can't see.
    @SuppressLint("MissingPermission")
    suspend fun notify(episodes: List<EpisodeDetailDto>) {
        if (episodes.isEmpty()) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel()
        episodes.forEach { episode ->
            manager.notify(episode.id.hashCode(), episodeNotification(episode))
        }
        manager.notify(SUMMARY_ID, summaryNotification(episodes))
    }

    private suspend fun episodeNotification(episode: EpisodeDetailDto) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_episode)
            .setContentTitle(episode.podcastName ?: "New episode")
            .setContentText(episode.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText(episode.title))
            .setLargeIcon(episode.podcastImage?.let { loadBitmap(it) })
            .setContentIntent(openEpisodeIntent(episode.id))
            .setGroup(GROUP_KEY)
            .setAutoCancel(true)
            .build()

    private fun summaryNotification(episodes: List<EpisodeDetailDto>) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_episode)
            .setContentTitle("New episodes")
            .setContentText("${episodes.size} new episodes")
            .setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    episodes.forEach { style.addLine("${it.podcastName}: ${it.title}") }
                },
            )
            .setContentIntent(openAppIntent())
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

    private fun openEpisodeIntent(episodeId: String): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "cast://episode/$episodeId".toUri(),
            context,
            MainActivity::class.java,
        )
        return PendingIntent.getActivity(
            context,
            episodeId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            SUMMARY_ID,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private suspend fun loadBitmap(url: String): Bitmap? =
        SingletonImageLoader.get(context)
            .execute(ImageRequest.Builder(context).data(url).allowHardware(false).build())
            .image
            ?.toBitmap()

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "New episodes",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "new_episodes"
        private const val GROUP_KEY = "cast.new_episodes"
        private const val SUMMARY_ID = 0
    }
}
