package com.bunnlist.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.HomeText
import com.bunnlist.core.RemoteLot
import java.util.Locale

/**
 * BunnList's coffee card (iOS BunnCoffeeCard): the bag lifted off a wash of its main flavour, a passport stamp
 * for its origin, the flavour strip — one band per family — and the rating. Press to lift the bag.
 */
@Composable
fun BunnCoffeeCard(lot: RemoteLot, modifier: Modifier = Modifier, rank: Int? = null, onClick: () -> Unit) {
    val family = HomeText.dominant(lot.flavorNotes)
    val tint = if (family == FlavorFamily.Other) Brand.Gold else family.color()
    val pressed = remember { mutableStateOf(false) }
    Column(modifier.shadow(10.dp, RoundedCornerShape(22.dp), ambientColor = tint.copy(alpha = 0.12f), spotColor = tint.copy(alpha = 0.12f))
        .clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)
        .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.20f), tint.copy(alpha = 0.04f))))
        .border(1.dp, tint.copy(alpha = 0.28f), RoundedCornerShape(22.dp)).pressable(pressedState = pressed, onClick = onClick)) {
        Box(Modifier.padding(5.dp)) {
            LotPhotoWell(lot.imageUrl, lot.flavorNotes, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(17.dp)), 6.dp, lifted = pressed.value,
                roaster = lot.roasterNameAr, name = lot.nameAr, origin = lot.originNameAr, originCode = lot.originCode)
            Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                rank?.let { Text("#$it", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White, modifier = Modifier.clip(CircleShape).background(Brand.Ink.copy(alpha = 0.75f)).padding(horizontal = 7.dp, vertical = 3.dp)) }
                lot.flag?.let { OriginStamp(it, tint) }
            }
        }
        FlavorStrip(HomeText.spectrum(lot.flavorNotes), Modifier.padding(horizontal = 10.dp).padding(top = 3.dp))
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(lot.nameAr, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(lot.roasterNameAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(lot.processLabel ?: com.bunnlist.core.Methods.processAr(lot.process), lot.originNameAr).joinToString(" · "),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (lot.reviewCount > 0) CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = Brand.Gold, modifier = Modifier.size(12.dp))
                        Text(String.format(Locale.ROOT, "%.1f", lot.averageRating), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(" (${lot.reviewCount})", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else Text("جديدة", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** One band per flavour family, in the fingerprint's colours; a quiet line when unknown. */
@Composable
fun FlavorStrip(families: List<FlavorFamily>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (families.isEmpty()) Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)))
        else families.forEach { Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(it.color())) }
    }
}

/** The origin as a small passport stamp: dashed ring, flag, slightly askew. */
@Composable
fun OriginStamp(flag: String, color: Color) {
    Box(Modifier.size(30.dp).rotate(-12f).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawCircle(color.copy(alpha = 0.8f), style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.5.dp.toPx(), 2.dp.toPx())))) }
        Text(flag, fontSize = 15.sp)
    }
}
