package com.hammer.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// §11: fond blanc cassé, carte blanche, bordure 1px, aucune ombre.
val HammerBackground = Color(0xFFE5E7EB)
val HammerCard = Color(0xFFFFFFFF)
val HammerBorder = Color(0xFFD1D5DB)
val HammerAccentStart = Color(0xFF22C55E)
val HammerAccentStop = Color(0xFFEF4444)
val HammerTextPrimary = Color(0xFF111827)
val HammerTextSecondary = Color(0xFF6B7280)

private val HammerColorScheme = lightColorScheme(
    primary = HammerAccentStart,
    error = HammerAccentStop,
    background = HammerBackground,
    surface = HammerCard,
    onBackground = HammerTextPrimary,
    onSurface = HammerTextPrimary
)

@Composable
fun HammerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HammerColorScheme, content = content)
}
