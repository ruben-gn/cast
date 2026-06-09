package cast.android.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.action.actionStartService
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDefaults
import androidx.glance.color.ColorProvider as dayNightColorProvider
import androidx.glance.unit.ColorProvider
import cast.android.R
import cast.android.service.PlaybackService
import cast.android.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NowPlayingWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { Content() }
    }

    @Composable
    private fun Content() {
        val prefs = currentState<Preferences>()
        val title = prefs[TITLE_KEY] ?: ""
        val podcast = prefs[PODCAST_KEY] ?: ""
        val isPlaying = prefs[IS_PLAYING_KEY] ?: false
        val hasEpisode = prefs[HAS_EPISODE_KEY] ?: false

        // Brand theme, following the system dark-mode setting (a widget can't see the app's
        // in-app theme override). Light = Linen ink/sienna; dark = Ember ink/ember.
        val textPrimary = dayNightColorProvider(day = Color(0xFF2B241E), night = Color(0xFFF4EAE4))
        val textMuted = dayNightColorProvider(day = Color(0xFF8E8175), night = Color(0xFFB3A398))
        val accent = dayNightColorProvider(day = Color(0xFFD8512A), night = Color(0xFFE86A45))

        val context = LocalContext.current
        val openApp = actionStartActivity(
            Intent(Intent.ACTION_VIEW, "cast://now-playing".toUri(), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_background))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clickable(openApp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (!hasEpisode) {
                Text(
                    text = "Nothing playing",
                    style = TextDefaults.defaultTextStyle.copy(color = textMuted),
                )
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = GlanceModifier.defaultWeight().wrapContentHeight(),
                    ) {
                        Text(
                            text = title,
                            style = TextDefaults.defaultTextStyle.copy(
                                color = textPrimary,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 2,
                        )
                        if (podcast.isNotEmpty()) {
                            Text(
                                text = podcast,
                                style = TextDefaults.defaultTextStyle.copy(color = textMuted),
                                maxLines = 1,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ControlButton(
                            iconRes = R.drawable.ic_widget_fast_rewind,
                            description = "Seek back",
                            action = PlaybackService.ACTION_SEEK_BACK,
                            tint = textMuted,
                        )
                        ControlButton(
                            iconRes = if (isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
                            description = if (isPlaying) "Pause" else "Play",
                            action = PlaybackService.ACTION_PLAY_PAUSE,
                            tint = accent,
                            // After a long app-close the process is dead and the app is in the background,
                            // where startService() is forbidden (Android 8+/12+) — a plain background start
                            // is silently dropped, which is why widget Play "does nothing". Interacting with
                            // a widget is an explicit exemption for startForegroundService(), so route Play
                            // through that so the service can come up and resume the last episode.
                            isForegroundService = true,
                        )
                        ControlButton(
                            iconRes = R.drawable.ic_widget_fast_forward,
                            description = "Seek forward",
                            action = PlaybackService.ACTION_SEEK_FORWARD,
                            tint = textMuted,
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ControlButton(
        iconRes: Int,
        description: String,
        action: String,
        tint: ColorProvider,
        // Seek buttons stay a plain background start: they only do anything when the service is already
        // alive, and FGS-starting a dead/empty player just to no-op a seek would risk the 5s
        // startForeground() deadline crash for no benefit. Only Play needs the FGS exemption.
        isForegroundService: Boolean = false,
    ) {
        val context = LocalContext.current
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier
                .size(40.dp)
                .clickable(
                    actionStartService(
                        Intent(context, PlaybackService::class.java).also { it.action = action },
                        isForegroundService = isForegroundService,
                    )
                ),
        )
    }

    companion object {
        val TITLE_KEY = stringPreferencesKey("widget_title")
        val PODCAST_KEY = stringPreferencesKey("widget_podcast")
        val IS_PLAYING_KEY = booleanPreferencesKey("widget_is_playing")
        val HAS_EPISODE_KEY = booleanPreferencesKey("widget_has_episode")

        suspend fun update(
            context: Context,
            title: String,
            podcast: String,
            isPlaying: Boolean,
            hasEpisode: Boolean,
        ) = withContext(Dispatchers.Main) {
            val manager = GlanceAppWidgetManager(context)
            val ids = manager.getGlanceIds(NowPlayingWidget::class.java)
            ids.forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    prefs[TITLE_KEY] = title
                    prefs[PODCAST_KEY] = podcast
                    prefs[IS_PLAYING_KEY] = isPlaying
                    prefs[HAS_EPISODE_KEY] = hasEpisode
                }
                NowPlayingWidget().update(context, id)
            }
        }
    }
}
