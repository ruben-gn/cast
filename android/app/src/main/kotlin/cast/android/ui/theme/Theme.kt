package cast.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LinenColorScheme = lightColorScheme(
    primary = SiennaPrimary,
    onPrimary = SiennaOnPrimary,
    primaryContainer = SiennaContainer,
    onPrimaryContainer = SiennaOnContainer,
    secondary = SiennaPrimary,
    onSecondary = SiennaOnPrimary,
    secondaryContainer = SiennaContainer,
    onSecondaryContainer = SiennaOnContainer,
    // Keep tertiary on-brand too, so nothing renders Material's default purple.
    tertiary = SiennaPrimary,
    onTertiary = SiennaOnPrimary,
    tertiaryContainer = SiennaContainer,
    onTertiaryContainer = SiennaOnContainer,
    background = LinenBackground,
    onBackground = InkOnSurface,
    surface = PaperSurface,
    onSurface = InkOnSurface,
    surfaceVariant = LinenSurfaceVariant,
    onSurfaceVariant = MutedOnSurface,
    surfaceTint = SiennaPrimary,
    outline = LinenOutline,
    outlineVariant = LinenOutlineVariant,
    // Warm neutral surface ramp (NavigationBar, elevated surfaces, menus).
    surfaceBright = LinenSurfaceBright,
    surfaceDim = LinenSurfaceDim,
    surfaceContainerLowest = LinenContainerLowest,
    surfaceContainerLow = LinenContainerLow,
    surfaceContainer = LinenContainer,
    surfaceContainerHigh = LinenContainerHigh,
    surfaceContainerHighest = LinenContainerHighest,
    inverseSurface = InkInverseSurface,
    inverseOnSurface = LinenInverseOnSurface,
    inversePrimary = SiennaInversePrimary,
)

// Cast is light-only by design (the "Linen Paper" brand theme). The darkTheme parameter
// is kept so existing callers and @Preview wiring don't break, but it is intentionally
// ignored — we always apply the linen scheme regardless of the system setting.
@Composable
fun CastTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = LinenColorScheme, content = content)
}
