package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val SplitAudioDarkColorScheme = darkColorScheme(
    primary = BluetoothEmerald,
    onPrimary = ConsoleBackground,
    primaryContainer = BluetoothContainer,
    onPrimaryContainer = BluetoothEmeraldSoft,
    secondary = SpeakerAmber,
    onSecondary = ConsoleBackground,
    secondaryContainer = SpeakerContainer,
    onSecondaryContainer = SpeakerAmber,
    tertiary = OriginCyan,
    onTertiary = ConsoleBackground,
    background = ConsoleBackground,
    onBackground = TextPrimary,
    surface = ConsoleSurface,
    onSurface = TextPrimary,
    surfaceVariant = ConsoleSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = ConsoleCardBorder,
    error = DangerRose
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = SplitAudioDarkColorScheme,
            typography = Typography,
            content = content
        )
    }
}
