package com.astralofthesun.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Gold = Color(0xFFF5C44A)
val GoldDark = Color(0xFFE6A90D)
val Primary = Color(0xFF6A63F2)
val Bg = Color(0xFF000000)
val Card = Color(0xFF060606)
val CardBorder = Color(0x1AFFFFFF) // ~10% white
val TextDim = Color(0x66FFFFFF)    // ~40% white
val TextFaint = Color(0x52FFFFFF)  // ~32% white

private val DarkColors = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    secondary = Gold,
    onSecondary = Color(0xFF7A5200),
    background = Bg,
    onBackground = Color.White,
    surface = Card,
    onSurface = Color.White,
    surfaceVariant = Card,
    onSurfaceVariant = TextDim,
    outline = CardBorder,
)

@Composable
fun AstralTheme(content: @Composable () -> Unit) {
    // The app is dark-only, matching the web design.
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
