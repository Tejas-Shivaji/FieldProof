package com.fieldproof.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF071718)
val Pine = Color(0xFF0F3D3E)
val PineLight = Color(0xFF1B5A5C)
val Clay = Color(0xFFC46B3A)
val Sand = Color(0xFFF4EFE6)
val Moss = Color(0xFF7D9A6A)
val Danger = Color(0xFFD45D4A)
val Amber = Color(0xFFE0A14A)

private val Scheme = darkColorScheme(
    primary = Clay,
    onPrimary = Sand,
    secondary = Moss,
    onSecondary = Ink,
    background = Ink,
    onBackground = Sand,
    surface = Pine,
    onSurface = Sand,
    surfaceVariant = PineLight,
    onSurfaceVariant = Sand.copy(alpha = 0.82f),
    error = Danger,
    onError = Sand,
    outline = Sand.copy(alpha = 0.28f),
)

@Composable
fun FieldProofTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        content = content,
    )
}
