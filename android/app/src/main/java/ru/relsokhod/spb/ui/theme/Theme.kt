package ru.relsokhod.spb.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ru.relsokhod.spb.model.AccentSeed
import ru.relsokhod.spb.model.ThemeMode

private val RelsokhodShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val TramLightColorScheme = lightColorScheme(
    primary = Color(0xFF8C1D2D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF6D9DD),
    onPrimaryContainer = Color(0xFF420811),
    secondary = Color(0xFF7D5800),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE5B8),
    onSecondaryContainer = Color(0xFF281A00),
    tertiary = Color(0xFF4E6542),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD0ECC0),
    onTertiaryContainer = Color(0xFF0E2005),
    background = Color(0xFFFBF6EE),
    onBackground = Color(0xFF261D19),
    surface = Color(0xFFFBF6EE),
    onSurface = Color(0xFF261D19),
    surfaceVariant = Color(0xFFF0E6DA),
    onSurfaceVariant = Color(0xFF5D4D44),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F0E6),
    surfaceContainer = Color(0xFFF2E9DD),
    surfaceContainerHigh = Color(0xFFECE1D3),
    surfaceContainerHighest = Color(0xFFE5D9CA),
    outline = Color(0xFF8A756A),
    outlineVariant = Color(0xFFDDCFC1)
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
            val seedPrimary = Color(accentSeed.primaryHex)
            val resolvedPrimary = when (accentSeed) {
                AccentSeed.NEVA, AccentSeed.SYSTEM -> Color(0xFF93C5FD)
                AccentSeed.SAGE -> Color(0xFF86EFAC)
                AccentSeed.TERRACOTTA -> Color(0xFFFDBA74)
                AccentSeed.INDIGO -> Color(0xFFA5B4FC)
                AccentSeed.AMBER -> Color(0xFFFCD34D)
            }
            val baseDark = Color(0xFF0F141C)
            darkColorScheme(
                primary = resolvedPrimary,
                onPrimary = Color(0xFF0A1929),
                primaryContainer = lerp(baseDark, seedPrimary, 0.36f),
                onPrimaryContainer = Color(0xFFE8F1FF),
                secondary = lerp(resolvedPrimary, Color(0xFFCBD5E1), 0.38f),
                onSecondary = Color(0xFF15202E),
                secondaryContainer = lerp(baseDark, seedPrimary, 0.24f),
                onSecondaryContainer = Color(0xFFDCE7F7),
                tertiary = Color(0xFF94D2BD),
                onTertiary = Color(0xFF09251D),
                background = baseDark,
                onBackground = Color(0xFFE6ECF5),
                surface = lerp(baseDark, resolvedPrimary, 0.04f),
                onSurface = Color(0xFFE6ECF5),
                surfaceVariant = lerp(baseDark, resolvedPrimary, 0.12f),
                onSurfaceVariant = Color(0xFFA9B7CB),
                surfaceContainerLowest = Color(0xFF0A0E14),
                surfaceContainerLow = lerp(baseDark, resolvedPrimary, 0.06f),
                surfaceContainer = lerp(baseDark, resolvedPrimary, 0.09f),
                surfaceContainerHigh = lerp(baseDark, resolvedPrimary, 0.13f),
                surfaceContainerHighest = lerp(baseDark, resolvedPrimary, 0.17f),
                outline = Color(0xFF6B7B93),
                outlineVariant = lerp(Color(0xFF283244), resolvedPrimary, 0.18f)
            )
        }
        else -> {
            val primary = Color(accentSeed.primaryHex)
            val container = Color(accentSeed.containerHex)
            val neutralLight = Color(0xFFF8FAFC)
            lightColorScheme(
                primary = primary,
                onPrimary = Color.White,
                primaryContainer = container,
                onPrimaryContainer = Color(0xFF0F2438),
                secondary = lerp(primary, Color(0xFF475569), 0.45f),
                onSecondary = Color.White,
                secondaryContainer = lerp(container, Color(0xFFF1F5F9), 0.42f),
                onSecondaryContainer = Color(0xFF1A2838),
                tertiary = Color(0xFF0F766E),
                onTertiary = Color.White,
                background = lerp(neutralLight, primary, 0.03f),
                onBackground = Color(0xFF161D26),
                surface = lerp(Color.White, primary, 0.015f),
                onSurface = Color(0xFF161D26),
                surfaceVariant = lerp(Color(0xFFEAEFF6), primary, 0.07f),
                onSurfaceVariant = Color(0xFF4C596B),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = lerp(neutralLight, primary, 0.045f),
                surfaceContainer = lerp(Color(0xFFF1F4F9), primary, 0.065f),
                surfaceContainerHigh = lerp(Color(0xFFEAEFF6), primary, 0.09f),
                surfaceContainerHighest = lerp(Color(0xFFE2E8F0), primary, 0.11f),
                outline = Color(0xFF748194),
                outlineVariant = lerp(Color(0xFFD5DEE8), primary, 0.12f)
            )
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        shapes = RelsokhodShapes,
        content = content
    )
}
