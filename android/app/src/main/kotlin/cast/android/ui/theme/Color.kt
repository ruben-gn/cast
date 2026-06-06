package cast.android.ui.theme

import androidx.compose.ui.graphics.Color

// Cast "Linen Paper" brand palette — a deliberate light theme replacing Material You
// dynamic color (which rendered washed-out on a black wallpaper). Light-only by design.
val SiennaPrimary = Color(0xFFD8512A)
val SiennaOnPrimary = Color(0xFFFFFFFF)
val LinenBackground = Color(0xFFFBF8F3)
val PaperSurface = Color(0xFFFFFFFF)
val InkOnSurface = Color(0xFF2B241E)
val MutedOnSurface = Color(0xFF8E8175)
val LinenSurfaceVariant = Color(0xFFECE5DB)
val LinenOutline = Color(0xFFDBD0C0)

// Container tints for the sienna accent (light surfaces tinted toward the primary).
val SiennaContainer = Color(0xFFFBE0D6)
val SiennaOnContainer = Color(0xFF4A1A0C)

// Warm neutral surface ramp. Material3 components (NavigationBar, elevated surfaces, menus)
// pull from these surfaceContainer* roles; left unset they fall back to Material's COOL gray
// baseline, which fights the warm linen. Keep them all warm off-whites.
val LinenContainerLowest = Color(0xFFFFFFFF)
val LinenContainerLow = Color(0xFFFCF9F4)
val LinenContainer = Color(0xFFF7F2EA)
val LinenContainerHigh = Color(0xFFF2ECE2)
val LinenContainerHighest = Color(0xFFECE5DB)
val LinenSurfaceBright = Color(0xFFFFFFFF)
val LinenSurfaceDim = Color(0xFFEBE3D7)
val LinenOutlineVariant = Color(0xFFE6DCCE)

// Inverse roles (snackbars, etc.) — dark warm ink instead of Material's cool default.
val InkInverseSurface = Color(0xFF2B241E)
val LinenInverseOnSurface = Color(0xFFFBF8F3)
val SiennaInversePrimary = Color(0xFFF0A98C)

// ---- Dark "Ember" counterpart -------------------------------------------------------------
// The dark theme keeps the warm sienna family but lifts the accent to a brighter ember so it
// reads on a dark page, with warm near-black surfaces and warm-white ink.
val EmberPrimary = Color(0xFFE86A45)
val EmberOnPrimary = Color(0xFF3A1B0E)
val EmberContainer = Color(0xFF6E2C16)
val EmberOnContainer = Color(0xFFFFD9CB)
val DarkBackground = Color(0xFF161210)
val DarkInk = Color(0xFFF4EAE4)
val DarkSurface = Color(0xFF221C18)
val DarkSurfaceVariant = Color(0xFF3A302A)
val DarkMuted = Color(0xFFB3A398)
val DarkOutline = Color(0xFF5A4F47)
val DarkOutlineVariant = Color(0xFF38302A)
val DarkContainerLowest = Color(0xFF110D0B)
val DarkContainerLow = Color(0xFF1C1714)
val DarkContainer = Color(0xFF221C18)
val DarkContainerHigh = Color(0xFF2C251F)
val DarkContainerHighest = Color(0xFF372F28)
val DarkSurfaceBright = Color(0xFF3A322B)
val DarkSurfaceDim = Color(0xFF161210)
val EmberInversePrimary = Color(0xFFD8512A)
