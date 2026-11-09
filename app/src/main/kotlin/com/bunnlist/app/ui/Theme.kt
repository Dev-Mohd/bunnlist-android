package com.bunnlist.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** BunnList brand colours. Dark mode is espresso with cream type and sage actions. */
object Brand {
    val Cream = Color(0xFFF7F1E8)
    val Ink = Color(0xFF171411)
    val Olive = Color(0xFF536344)
    val Sage = Color(0xFFA3B68F)
    val Gold = Color(0xFF987024)
    val GoldLight = Color(0xFFE2B75C)
    val Brown = Color(0xFF4A3428)
    val Espresso = Color(0xFF1A1310)
}

private val Light = lightColorScheme(
    primary = Brand.Olive, onPrimary = Color.White, primaryContainer = Color(0xFFDDE5D2), onPrimaryContainer = Color(0xFF1E2A14),
    secondary = Brand.Gold, onSecondary = Color.White, secondaryContainer = Color(0xFFEFE2C6), onSecondaryContainer = Color(0xFF3A2A0C),
    tertiary = Brand.Brown, onTertiary = Color.White, tertiaryContainer = Color(0xFFE9DCD3), onTertiaryContainer = Brand.Brown,
    background = Brand.Cream, onBackground = Brand.Ink, surface = Color.White, onSurface = Brand.Ink,
    surfaceVariant = Color(0xFFEDE4D6), onSurfaceVariant = Color(0xFF6B6258), outline = Color(0xFFD9CDBB), outlineVariant = Color(0xFFE6DCCB),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBF7F0), surfaceContainer = Color(0xFFF3ECE1),
    surfaceContainerHigh = Color(0xFFEDE4D6), surfaceContainerHighest = Color(0xFFE6DCCB), surfaceTint = Brand.Olive,
)

private val Dark = darkColorScheme(
    primary = Brand.Sage, onPrimary = Brand.Espresso, primaryContainer = Color(0xFF3A4730), onPrimaryContainer = Color(0xFFDDE7D0),
    secondary = Brand.GoldLight, onSecondary = Brand.Espresso, secondaryContainer = Color(0xFF4A3A1A), onSecondaryContainer = Color(0xFFF3E3BE),
    tertiary = Color(0xFFC9A891), onTertiary = Brand.Espresso, tertiaryContainer = Color(0xFF3E2C22), onTertiaryContainer = Color(0xFFE9DCD3),
    background = Brand.Espresso, onBackground = Brand.Cream, surface = Color(0xFF251C17), onSurface = Brand.Cream,
    surfaceVariant = Color(0xFF30251F), onSurfaceVariant = Color(0xFFC9BBA8), outline = Color(0xFF4A3D34), outlineVariant = Color(0xFF3A2F28),
    surfaceContainerLowest = Color(0xFF140E0C), surfaceContainerLow = Color(0xFF1E1713), surfaceContainer = Color(0xFF211914),
    surfaceContainerHigh = Color(0xFF2A211B), surfaceContainerHighest = Color(0xFF342A23), surfaceTint = Brand.Sage,
)

/** «المظهر»: "system", "light" or "dark", kept on the phone. */
object ThemeMode {
    var mode by androidx.compose.runtime.mutableStateOf("system")
    fun load(context: android.content.Context) { mode = context.getSharedPreferences("ui", 0).getString("theme", "system") ?: "system" }
    fun set(context: android.content.Context, value: String) { mode = value; context.getSharedPreferences("ui", 0).edit().putString("theme", value).apply() }
}

@Composable
fun BunnListTheme(content: @Composable () -> Unit) {
    val dark = when (ThemeMode.mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }
    val scheme = if (dark) Dark else Light
    // Text with no colour of its own takes LocalContentColor, which is black unless something sets it. Without
    // this, titles on plain backgrounds (not inside a Surface) stayed black in dark mode.
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, LocalDarkTheme provides dark, content = content)
    }
}

/** Whether BunnList is drawing dark right now («المظهر» or the system). */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** Brand colours for text and small marks: the light-mode brand colour, or a lighter cousin that reads on espresso. */
object Tone {
    val olive: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Brand.Sage else Brand.Olive
    val gold: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Brand.GoldLight else Brand.Gold
    val brown: Color @Composable @ReadOnlyComposable get() = if (LocalDarkTheme.current) Color(0xFFD9BFA9) else Brand.Brown
}
