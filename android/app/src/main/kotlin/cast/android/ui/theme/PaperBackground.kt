package cast.android.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import cast.android.R

// Draw alpha for the grain overlay. The PNG itself is baked at a low peak alpha; this
// multiplies it down to the final paper-tooth subtlety. TUNE THIS ON A REAL DEVICE — a
// value that looks right in a browser/emulator is not guaranteed to match a phone panel.
private const val GrainAlpha = 0.55f

/**
 * Fills the screen with the Linen page color and a seamlessly-tiled paper-grain overlay,
 * then hosts [content] on top. The grain lives only on this page background; surfaces
 * (cards, sheets, bars) stay clean so text contrast is never affected.
 */
@Composable
fun PaperBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val grain = ImageBitmap.imageResource(R.drawable.paper_grain)
    val paper = MaterialTheme.colorScheme.background
    val brush = remember(grain) {
        ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated))
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = paper)
                drawRect(brush = brush, alpha = GrainAlpha)
            },
    ) {
        content()
    }
}
