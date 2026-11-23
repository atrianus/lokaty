package pl.lokaty.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF0B6E4F)
private val LightScheme = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEEDD),
    onPrimaryContainer = Color(0xFF002114),
    secondaryContainer = Color(0xFFDDE8E1),
    onSecondaryContainer = Color(0xFF111E17),
    tertiary = Color(0xFF8A5A00),
    tertiaryContainer = Color(0xFFFFE2B3),
    onTertiaryContainer = Color(0xFF2B1800),
    background = Color(0xFFF7FAF8),
    surface = Color(0xFFF7FAF8),
    surfaceVariant = Color(0xFFE6ECE8),
    onSurfaceVariant = Color(0xFF404943),
    error = Color(0xFFB3261E),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF7DD7AB),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFCDEEDD),
    secondaryContainer = Color(0xFF2B3A31),
    onSecondaryContainer = Color(0xFFDDE8E1),
    tertiary = Color(0xFFFFB950),
    tertiaryContainer = Color(0xFF5E3D00),
    onTertiaryContainer = Color(0xFFFFE2B3),
    background = Color(0xFF101412),
    surface = Color(0xFF101412),
    surfaceVariant = Color(0xFF3F4943),
    onSurfaceVariant = Color(0xFFBFC9C2),
    error = Color(0xFFF2B8B5),
)

/** Zysk (dodatni) w kolorze głównym, żeby kwoty „na plus” były czytelne w obu trybach. */
@Composable
fun LokatyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkScheme else LightScheme, content = content)
}
