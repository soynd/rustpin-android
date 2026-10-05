package com.rustpin.android.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.rustpin.android.store.Accent

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color.White,
    secondaryContainer = accent.copy(alpha = 0.25f),
    background = Color(0xFF111111),
    surface = Color(0xFF1B1B1B),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFE8E8E8),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFBDBDBD),
)

/** App theme: dark only, with the chosen accent color. */
@Composable
fun RustpinTheme(
    accent: Accent = Accent.DEFAULT,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = darkScheme(accent.color),
        content = content,
    )
}
