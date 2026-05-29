package cast.android.widget

import androidx.glance.appwidget.GlanceAppWidgetReceiver

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = NowPlayingWidget()
}
