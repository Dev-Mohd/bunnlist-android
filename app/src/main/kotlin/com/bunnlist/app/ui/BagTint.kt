package com.bunnlist.app.ui

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The bag photo's own colour, so each coffee tints its screen. Null until read, or for a plain bag. */
@Composable
fun rememberBagColor(url: String?): Color? {
    val context = LocalContext.current
    var color by remember(url) { mutableStateOf<Color?>(null) }
    LaunchedEffect(url) {
        if (url == null) return@LaunchedEffect
        val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).size(160).allowHardware(false).build())
        val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        color = withContext(Dispatchers.Default) { pick(bitmap) }
    }
    return color
}

private fun pick(bitmap: Bitmap): Color? {
    // Bags are shot on white: ignore near-white and near-grey pixels so the bag decides.
    val palette = Palette.from(bitmap).maximumColorCount(16).addFilter { rgb, hsl -> hsl[2] < 0.92f && hsl[1] > 0.12f }.generate()
    val swatch = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch ?: return null
    return Color(swatch.rgb)
}
