package cast.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

private val EmberColorScheme = darkColorScheme(
    primary = EmberPrimary,
    onPrimary = EmberOnPrimary,
    primaryContainer = EmberContainer,
    onPrimaryContainer = EmberOnContainer,
    secondary = EmberPrimary,
    onSecondary = EmberOnPrimary,
    secondaryContainer = EmberContainer,
    onSecondaryContainer = EmberOnContainer,
    tertiary = EmberPrimary,
    onTertiary = EmberOnPrimary,
    tertiaryContainer = EmberContainer,
    onTertiaryContainer = EmberOnContainer,
    background = DarkBackground,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    surfaceTint = EmberPrimary,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceBright = DarkSurfaceBright,
    surfaceDim = DarkSurfaceDim,
    surfaceContainerLowest = DarkContainerLowest,
    surfaceContainerLow = DarkContainerLow,
    surfaceContainer = DarkContainer,
    surfaceContainerHigh = DarkContainerHigh,
    surfaceContainerHighest = DarkContainerHighest,
    inverseSurface = DarkInk,
    inverseOnSurface = InkOnSurface,
    inversePrimary = EmberInversePrimary,
)

// Cast's brand theme. Light is the "Linen Paper" look; dark is its warm "Ember" counterpart.
// The caller resolves [darkTheme] from the user's ThemeMode preference (System/Light/Dark).
@Composable
fun CastTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) EmberColorScheme else LinenColorScheme,
        content = content,
    )
}
