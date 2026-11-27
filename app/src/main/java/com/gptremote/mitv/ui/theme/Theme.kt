package com.gptremote.mitv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

enum class AppAccentTheme(val label: String, val primary: Color, val soft: Color) {
    ORANGE("Mi Orange", Color(0xFFFF6900), Color(0xFFFF8C3A)),
    BLUE("Ocean Blue", Color(0xFF38BDF8), Color(0xFF7DD3FC)),
    EMERALD("Emerald", Color(0xFF10B981), Color(0xFF34D399)),
    PURPLE("Royal Purple", Color(0xFFA855F7), Color(0xFFC084FC)),
    CRIMSON("Crimson", Color(0xFFF43F5E), Color(0xFFFB7185))
}

val LocalAccentColor = compositionLocalOf { Color(0xFFFF6900) }
val LocalAccentSoft = compositionLocalOf { Color(0xFFFF8C3A) }

val MiOrange: Color
    @Composable get() = LocalAccentColor.current

val RemoteBg = Color(0xFF0E1015)
val RemoteSurface = Color(0xFF181B23)
val RemoteSurfaceElevated = Color(0xFF232834)
val RemoteBorder = Color(0xFF2F3545)
val ConnectedGreen = Color(0xFF34D399)
val WifiBlue = Color(0xFF38BDF8)
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)

@Composable
fun MiTvRemoteTheme(
    accentTheme: AppAccentTheme = AppAccentTheme.ORANGE,
    content: @Composable () -> Unit
) {
    val colors = darkColorScheme(
        primary = accentTheme.primary,
        onPrimary = Color.White,
        primaryContainer = accentTheme.primary.copy(alpha = 0.24f),
        onPrimaryContainer = TextPrimary,
        secondary = accentTheme.soft,
        onSecondary = Color(0xFF062033),
        secondaryContainer = accentTheme.primary.copy(alpha = 0.20f),
        onSecondaryContainer = accentTheme.soft,
        tertiary = WifiBlue,
        inversePrimary = accentTheme.soft,
        background = RemoteBg,
        onBackground = TextPrimary,
        surface = RemoteSurface,
        onSurface = TextPrimary,
        surfaceVariant = RemoteSurfaceElevated,
        onSurfaceVariant = TextSecondary,
        outline = RemoteBorder,
        error = Color(0xFFF87171),
        onError = Color.Black
    )
    CompositionLocalProvider(
        LocalAccentColor provides accentTheme.primary,
        LocalAccentSoft provides accentTheme.soft
    ) {
        MaterialTheme(
            colorScheme = colors,
            content = content
        )
    }
}
