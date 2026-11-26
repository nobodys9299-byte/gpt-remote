package com.gptremote.mitv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MiOrange = Color(0xFFFF6900)
val MiOrangeSoft = Color(0xFFFF8C3A)
val RemoteBg = Color(0xFF0E1015)
val RemoteSurface = Color(0xFF181B23)
val RemoteSurfaceElevated = Color(0xFF232834)
val RemoteBorder = Color(0xFF2F3545)
val ConnectedGreen = Color(0xFF34D399)
val WifiBlue = Color(0xFF38BDF8)
val TextPrimary = Color(0xFFF3F4F6)
val TextSecondary = Color(0xFF9CA3AF)

private val RemoteDarkColors = darkColorScheme(
    primary = MiOrange,
    onPrimary = Color.White,
    secondary = WifiBlue,
    onSecondary = Color(0xFF062033),
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

@Composable
fun MiTvRemoteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RemoteDarkColors,
        content = content
    )
}
