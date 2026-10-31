package com.lordcb.homelauncher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object Palette {
    val Background = Color(0xFF0A0E14)
    val BackgroundTop = Color(0xFF111A27)
    val Card = Color(0xFF161D27)
    val CardActive = Color(0xFF2A2417)
    val Focused = Color(0xFFF2F4F7)
    val OnFocused = Color(0xFF0A0E14)
    val TextPrimary = Color(0xFFF5F7FA)
    val TextSecondary = Color(0xFF9AA5B4)
    val Accent = Color(0xFF7CC4FF)
    val Warm = Color(0xFFFFC65C)
    val Good = Color(0xFF7BD88F)
    val Bad = Color(0xFFFF8A80)
}

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Accent,
            onPrimary = Palette.OnFocused,
            background = Palette.Background,
            onBackground = Palette.TextPrimary,
            surface = Palette.Card,
            onSurface = Palette.TextPrimary,
        ),
        content = content,
    )
}
