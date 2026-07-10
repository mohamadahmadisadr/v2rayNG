package dev.sadr.atlas.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AccentBlue = Color(0xFF4C7DFF)
val AccentBlueDeep = Color(0xFF2F5BE7)
val SuccessGreen = Color(0xFF22C55E)
val SuccessGreenDeep = Color(0xFF15803D)
val WarningAmber = Color(0xFFF59E0B)
val DangerRed = Color(0xFFEF4444)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1C2A4E),
    onPrimaryContainer = Color(0xFFBCCCFF),
    secondary = Color(0xFF7EA0FF),
    onSecondary = Color(0xFF0A0F1E),
    secondaryContainer = Color(0xFF1A2440),
    onSecondaryContainer = Color(0xFFB9C6EE),
    tertiary = SuccessGreen,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF11301F),
    onTertiaryContainer = Color(0xFF86EFAC),
    background = Color(0xFF0A0F1E),
    onBackground = Color(0xFFE6EAF5),
    surface = Color(0xFF0F1626),
    onSurface = Color(0xFFE6EAF5),
    surfaceVariant = Color(0xFF182136),
    onSurfaceVariant = Color(0xFF9AA6C3),
    surfaceContainer = Color(0xFF121A2C),
    surfaceContainerLow = Color(0xFF101828),
    surfaceContainerHigh = Color(0xFF1A2340),
    surfaceContainerHighest = Color(0xFF212C4D),
    outline = Color(0xFF3A4668),
    outlineVariant = Color(0xFF232E4C),
    error = DangerRed,
    onError = Color.White
)

private val LightColors = lightColorScheme(
    primary = AccentBlueDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE6FF),
    onPrimaryContainer = Color(0xFF162A5C),
    secondary = Color(0xFF4A5B8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E9FB),
    onSecondaryContainer = Color(0xFF23304F),
    tertiary = SuccessGreenDeep,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD9F5E2),
    onTertiaryContainer = Color(0xFF0E4A26),
    background = Color(0xFFF4F6FB),
    onBackground = Color(0xFF121729),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF121729),
    surfaceVariant = Color(0xFFEAEEF8),
    onSurfaceVariant = Color(0xFF5B6580),
    surfaceContainer = Color(0xFFF7F9FE),
    surfaceContainerLow = Color(0xFFFAFBFF),
    surfaceContainerHigh = Color(0xFFEFF3FC),
    surfaceContainerHighest = Color(0xFFE7ECF9),
    outline = Color(0xFFC3CCE3),
    outlineVariant = Color(0xFFDCE2F2),
    error = Color(0xFFDC2626),
    onError = Color.White
)

@Composable
fun V2Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
