package com.bunnlist.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** BunnList brand colours, the same tokens as the iOS asset catalog. */
object Brand {
    val Cream = Color(0xFFF7F1E8)
    val Ink = Color(0xFF171411)
    val Olive = Color(0xFF536344)
    val OliveDark = Color(0xFF788F64)
    val Gold = Color(0xFF987024)
    val GoldDark = Color(0xFFE2B75C)
    val Brown = Color(0xFF4A3428)
}

private val Light = lightColorScheme(
    primary = Brand.Olive, onPrimary = Color.White, secondary = Brand.Gold, onSecondary = Color.White,
    background = Brand.Cream, onBackground = Brand.Ink, surface = Color.White, onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFEDE4D6), onSurfaceVariant = Color(0xFF6B6258), outline = Color(0xFFD9CDBB),
    primaryContainer = Color(0xFFDDE5D2), onPrimaryContainer = Color(0xFF1E2A14),
    secondaryContainer = Color(0xFFEFE2C6), onSecondaryContainer = Color(0xFF3A2A0C),
    tertiary = Brand.Brown, onTertiary = Color.White, tertiaryContainer = Color(0xFFE9DCD3), onTertiaryContainer = Brand.Brown,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBF7F0), surfaceContainer = Color(0xFFF3ECE1),
    surfaceContainerHigh = Color(0xFFEDE4D6), surfaceContainerHighest = Color(0xFFE6DCCB), surfaceTint = Brand.Olive,
)
private val Dark = darkColorScheme(
    primary = Brand.OliveDark, onPrimary = Brand.Ink, secondary = Brand.GoldDark, onSecondary = Brand.Ink,
    background = Brand.Ink, onBackground = Color(0xFFF5EFE6), surface = Color(0xFF211D19), onSurface = Color(0xFFF5EFE6),
    surfaceVariant = Color(0xFF2C2620), onSurfaceVariant = Color(0xFFB3A897), outline = Color(0xFF3A332B),
    primaryContainer = Color(0xFF34402A), onPrimaryContainer = Color(0xFFDDE5D2),
    secondaryContainer = Color(0xFF4A3A1A), onSecondaryContainer = Color(0xFFF3E3BE),
    tertiary = Color(0xFFC9A891), onTertiary = Brand.Ink, tertiaryContainer = Color(0xFF3E2C22), onTertiaryContainer = Color(0xFFE9DCD3),
    surfaceContainerLowest = Color(0xFF120F0D), surfaceContainerLow = Color(0xFF1C1915), surfaceContainer = Color(0xFF211D19),
    surfaceContainerHigh = Color(0xFF2A251F), surfaceContainerHighest = Color(0xFF332D26), surfaceTint = Brand.OliveDark,
)

@Composable
fun BunnListTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
