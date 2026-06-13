package cast.android.ui.components

import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat

@Composable
fun EpisodeDescription(html: String) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextColor(textColor)
                setLinkTextColor(linkColor)
            }
        },
        update = { tv ->
            val spanned = SpannableString(HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT))
            Linkify.addLinks(spanned, Linkify.WEB_URLS)
            tv.text = spanned
            tv.movementMethod = LinkMovementMethod.getInstance()
        },
    )
}
