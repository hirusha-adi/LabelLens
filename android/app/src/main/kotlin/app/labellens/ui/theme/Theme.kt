// Label Lens theme, from the design tokens (Foundations board).
package app.labellens.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.labellens.R

// ---------- Colour ----------
val lightScheme = lightColorScheme(
    primary = Color(0xFF3E6A00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBEF27A),
    onPrimaryContainer = Color(0xFF102000),
    secondary = Color(0xFF8C4F00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC1),
    onSecondaryContainer = Color(0xFF2D1600),
    tertiary = Color(0xFF006B5B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF9EF2DD),
    onTertiaryContainer = Color(0xFF00201A),
    error = Color(0xFFB52A1C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD4),
    onErrorContainer = Color(0xFF410001),
    background = Color(0xFFFAFBEF),
    onBackground = Color(0xFF1A1C15),
    surface = Color(0xFFFAFBEF),
    onSurface = Color(0xFF1A1C15),
    surfaceVariant = Color(0xFFE1E4D4),
    onSurfaceVariant = Color(0xFF44483B),
    surfaceTint = Color(0xFF3E6A00),
    inverseSurface = Color(0xFF2F3129),
    inverseOnSurface = Color(0xFFF1F2E5),
    inversePrimary = Color(0xFFA2D663),
    outline = Color(0xFF75796A),
    outlineVariant = Color(0xFFC5C8B8),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFAFBEF),
    surfaceDim = Color(0xFFDADBD0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F5E9),
    surfaceContainer = Color(0xFFEEEFE3),
    surfaceContainerHigh = Color(0xFFE8E9DE),
    surfaceContainerHighest = Color(0xFFE3E4D8),
)

val darkScheme = darkColorScheme(
    primary = Color(0xFFA2D663),
    onPrimary = Color(0xFF1F3700),
    primaryContainer = Color(0xFF2E5000),
    onPrimaryContainer = Color(0xFFBEF27A),
    secondary = Color(0xFFFFB878),
    onSecondary = Color(0xFF4B2800),
    secondaryContainer = Color(0xFF6B3B00),
    onSecondaryContainer = Color(0xFFFFDCC1),
    tertiary = Color(0xFF82D5C1),
    onTertiary = Color(0xFF00382E),
    tertiaryContainer = Color(0xFF005144),
    onTertiaryContainer = Color(0xFF9EF2DD),
    error = Color(0xFFFFB4A8),
    onError = Color(0xFF690B02),
    errorContainer = Color(0xFF8F1A0D),
    onErrorContainer = Color(0xFFFFDAD4),
    background = Color(0xFF12140D),
    onBackground = Color(0xFFE2E4D6),
    surface = Color(0xFF12140D),
    onSurface = Color(0xFFE2E4D6),
    surfaceVariant = Color(0xFF44483B),
    onSurfaceVariant = Color(0xFFC5C8B8),
    surfaceTint = Color(0xFFA2D663),
    inverseSurface = Color(0xFFE2E4D6),
    inverseOnSurface = Color(0xFF2F3129),
    inversePrimary = Color(0xFF3E6A00),
    outline = Color(0xFF8F9284),
    outlineVariant = Color(0xFF44483B),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF383A32),
    surfaceDim = Color(0xFF12140D),
    surfaceContainerLowest = Color(0xFF0D0F09),
    surfaceContainerLow = Color(0xFF1A1C15),
    surfaceContainer = Color(0xFF1E2019),
    surfaceContainerHigh = Color(0xFF282B22),
    surfaceContainerHighest = Color(0xFF33352D),
)

@Immutable
data class StatusColors(val color: Color, val onColor: Color, val container: Color, val onContainer: Color)

@Immutable
data class StatusPalette(
    val safe: StatusColors,
    val caution: StatusColors,
    val avoid: StatusColors,
    /** The leaf in the logo mark. */
    val leaf: Color,
    /** Dimming layer behind sheets and dialogs. */
    val scrim: Color,
)

val LightStatus = StatusPalette(
    safe = StatusColors(Color(0xFF2B6A32), Color(0xFFFFFFFF), Color(0xFFC9F0C0), Color(0xFF0E3B12)),
    caution = StatusColors(Color(0xFF7A5B00), Color(0xFFFFFFFF), Color(0xFFFFE08A), Color(0xFF3D2E00)),
    avoid = StatusColors(Color(0xFFB52A1C), Color(0xFFFFFFFF), Color(0xFFFFDAD4), Color(0xFF5C0F05)),
    leaf = Color(0xFF5C9A12),
    scrim = Color(0x6612140D),
)
val DarkStatus = StatusPalette(
    safe = StatusColors(Color(0xFF9AD596), Color(0xFF00390C), Color(0xFF1F4D22), Color(0xFFB9F0B0)),
    caution = StatusColors(Color(0xFFEEC24A), Color(0xFF3F2E00), Color(0xFF574400), Color(0xFFFFE08A)),
    avoid = StatusColors(Color(0xFFFFB4A8), Color(0xFF690B02), Color(0xFF7A2416), Color(0xFFFFDAD4)),
    leaf = Color(0xFF6FA61F),
    scrim = Color(0x8C000000),
)
val LocalStatus = staticCompositionLocalOf { LightStatus }

/** Content colour on inversePrimary, which Material's ColorScheme has no slot for. */
val LocalInversePrimaryContent = staticCompositionLocalOf { Color(0xFF102000) }

// ---------- Type ----------
// Both families ship as variable fonts in res/font, so the app does not depend on
// the Google Fonts provider being available on the device.
private fun gabaritoFont(weight: Int) = Font(
    R.font.gabarito,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun hankenFont(weight: Int) = Font(
    R.font.hanken_grotesk,
    FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Gabarito = FontFamily(gabaritoFont(700), gabaritoFont(800), gabaritoFont(900))
val Hanken = FontFamily(hankenFont(400), hankenFont(500), hankenFont(600), hankenFont(700), hankenFont(800))

private fun s(f: FontFamily, w: Int, size: Int, lh: Int, ls: Double = 0.0) =
    TextStyle(fontFamily = f, fontWeight = FontWeight(w), fontSize = size.sp, lineHeight = lh.sp, letterSpacing = ls.sp)

/** Gabarito at an exact size, for the display and title styles the screens spell out. */
fun gab(weight: Int, size: Int, lineHeight: Int = 0, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = Gabarito,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = if (lineHeight > 0) lineHeight.sp else TextUnit.Unspecified,
    letterSpacing = letterSpacing.sp,
)

/** Hanken Grotesk at an exact size. */
fun hg(weight: Int, size: Int, lineHeight: Int = 0, letterSpacing: Double = 0.0) = TextStyle(
    fontFamily = Hanken,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = if (lineHeight > 0) lineHeight.sp else TextUnit.Unspecified,
    letterSpacing = letterSpacing.sp,
)

val LabelLensTypography = Typography(
    displayLarge = s(Gabarito, 800, 57, 64, -0.25),
    displayMedium = s(Gabarito, 800, 45, 52),
    displaySmall = s(Gabarito, 800, 36, 44),
    headlineLarge = s(Gabarito, 800, 32, 40),
    headlineMedium = s(Gabarito, 800, 28, 36),
    headlineSmall = s(Gabarito, 700, 24, 32),
    titleLarge = s(Gabarito, 700, 22, 28),
    titleMedium = s(Hanken, 700, 16, 24, 0.15),
    titleSmall = s(Hanken, 700, 14, 20, 0.1),
    bodyLarge = s(Hanken, 400, 16, 24, 0.15),
    bodyMedium = s(Hanken, 400, 14, 20, 0.25),
    bodySmall = s(Hanken, 400, 12, 16, 0.4),
    labelLarge = s(Hanken, 700, 14, 20, 0.1),
    labelMedium = s(Hanken, 600, 12, 16, 0.5),
    labelSmall = s(Hanken, 600, 11, 16, 0.5),
)

// Additive code badge: Gabarito 800 13/16, tabular figures ("tnum").
val CodeBadgeStyle = s(Gabarito, 800, 13, 16).copy(fontFeatureSettings = "tnum")

// ---------- Shape ----------
val LabelLensShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun LabelLensTheme(dark: Boolean, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalStatus provides if (dark) DarkStatus else LightStatus,
        LocalInversePrimaryContent provides if (dark) Color(0xFFFFFFFF) else Color(0xFF102000),
    ) {
        MaterialTheme(
            colorScheme = if (dark) darkScheme else lightScheme,
            typography = LabelLensTypography,
            shapes = LabelLensShapes,
            content = content,
        )
    }
}
