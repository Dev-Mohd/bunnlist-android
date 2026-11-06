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
)
private val Dark = darkColorScheme(
    primary = Brand.OliveDark, onPrimary = Brand.Ink, secondary = Brand.GoldDark, onSecondary = Brand.Ink,
    background = Brand.Ink, onBackground = Color(0xFFF5EFE6), surface = Color(0xFF211D19), onSurface = Color(0xFFF5EFE6),
    surfaceVariant = Color(0xFF2C2620), onSurfaceVariant = Color(0xFFB3A897), outline = Color(0xFF3A332B),
)

@Composable
fun BunnListTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
