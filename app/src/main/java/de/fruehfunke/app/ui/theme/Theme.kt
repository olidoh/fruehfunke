package de.fruehfunke.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.fruehfunke.app.data.AppSettings
import de.fruehfunke.app.data.ThemeMode

// Eigene Farbwelt "Morgenröte" für den Fall, dass dynamische Farben aus sind.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF8F4C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDCC2),
    onPrimaryContainer = Color(0xFF2E1500),
    secondary = Color(0xFF735943),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC2),
    onSecondaryContainer = Color(0xFF2A1706),
    tertiary = Color(0xFF5B6237),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE0E7B1),
    onTertiaryContainer = Color(0xFF181E00),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A14),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A14),
    surfaceVariant = Color(0xFFF3DFD2),
    onSurfaceVariant = Color(0xFF52443A),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF1E9),
    surfaceContainer = Color(0xFFFCEBE0),
    surfaceContainerHigh = Color(0xFFF6E5DA),
    surfaceContainerHighest = Color(0xFFF0DFD4),
    outline = Color(0xFF85746A),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFFFB77C),
    onPrimary = Color(0xFF4C2700),
    primaryContainer = Color(0xFF6D3A00),
    onPrimaryContainer = Color(0xFFFFDCC2),
    secondary = Color(0xFFE3C0A5),
    onSecondary = Color(0xFF412C19),
    secondaryContainer = Color(0xFF5A422D),
    onSecondaryContainer = Color(0xFFFFDCC2),
    tertiary = Color(0xFFC4CB97),
    onTertiary = Color(0xFF2D330D),
    tertiaryContainer = Color(0xFF434A22),
    onTertiaryContainer = Color(0xFFE0E7B1),
    background = Color(0xFF19120C),
    onBackground = Color(0xFFF0DFD4),
    surface = Color(0xFF19120C),
    onSurface = Color(0xFFF0DFD4),
    surfaceVariant = Color(0xFF52443A),
    onSurfaceVariant = Color(0xFFD7C3B6),
    surfaceContainerLowest = Color(0xFF130D07),
    surfaceContainerLow = Color(0xFF221A13),
    surfaceContainer = Color(0xFF261E17),
    surfaceContainerHigh = Color(0xFF312821),
    surfaceContainerHighest = Color(0xFF3C332B),
    outline = Color(0xFF9F8D82),
)

@Composable
fun FruehfunkeTheme(content: @Composable () -> Unit) {
    val settings by AppSettings.state.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val scheme = when {
        settings.dynamicColor && dark -> dynamicDarkColorScheme(context)
        settings.dynamicColor -> dynamicLightColorScheme(context)
        dark -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
