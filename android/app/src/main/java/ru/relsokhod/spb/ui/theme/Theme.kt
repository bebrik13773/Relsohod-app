package ru.relsokhod.spb.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.ThemeMode

private val TramLightColorScheme = lightColorScheme(
    primary = Color(0xFF8C1D2D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6D9DD),
    onPrimaryContainer = Color(0xFF420811),
    secondary = Color(0xFF7D5800),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE0A3),
    onSecondaryContainer = Color(0xFF281A00),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF261D19),
    surface = Color(0xFFFBF6EE),
    onSurface = Color(0xFF261D19),
    surfaceVariant = Color(0xFFF0E6DA),
    onSurfaceVariant = Color(0xFF5D4D44)
)

@Composable
fun RelsokhodTheme(
    themeMode: ThemeMode,
    accentSeed: AccentSeed,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val useDark = themeMode == ThemeMode.DARK

    val colorScheme = when {
        themeMode == ThemeMode.TRAM -> TramLightColorScheme
        accentSeed == AccentSeed.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        useDark -> {
            val primary = Color(accentSeed.primaryHex)
            val container = Color(accentSeed.primaryHex).copy(alpha = 0.28f)
            darkColorScheme(
                primary = Color(0xFF93C5FD).takeIf { accentSeed == AccentSeed.NEVA } ?: primary,
                primaryContainer = container,
                onPrimaryContainer = Color(0xFFE5EEFF),
                background = Color(0xFF0E131B),
                surface = Color(0xFF131A24),
                surfaceVariant = Color(0xFF1D2736)
            )
        }
        else -> {
            val primary = Color(accentSeed.primaryHex)
            val container = Color(accentSeed.containerHex)
            lightColorScheme(
                primary = primary,
                onPrimary = Color.White,
                primaryContainer = container,
                onPrimaryContainer = Color(0xFF0F2438),
                background = Color(0xFFF5F7FA),
                surface = Color(0xFFFFFFFF),
                surfaceVariant = Color(0xFFEBF0F6)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
