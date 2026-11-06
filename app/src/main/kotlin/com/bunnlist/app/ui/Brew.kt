package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.BrewTimeline
import com.bunnlist.core.Methods
import kotlinx.coroutines.delay

/** «حضّر»: pick a method, then a starting recipe. */
@Composable
fun BrewScreen(nav: Nav, initialMethod: String?, lotName: String?, onLotCleared: () -> Unit) {
    var method by remember(initialMethod) { mutableStateOf(initialMethod?.takeIf { it in BrewPreset.methods } ?: "V60") }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Text("حضّر", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        lotName?.let {
            InputChip(selected = true, onClick = onLotCleared, label = { Text("تحضّر: $it") },
                trailingIcon = { Icon(Icons.Filled.Close, "إزالة", Modifier.size(16.dp)) }, modifier = Modifier.padding(horizontal = 16.dp))
        }
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BrewPreset.methods.forEach { m -> FilterChip(selected = method == m, onClick = { method = m }, label = { Text(Methods.nameAr(m)) }) }
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(BrewPreset.presets(method), key = { it.id }) { p ->
                Card(onClick = { nav.push(Route.Brewing(p, lotName)) }, shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.nameAr, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(p.summaryAr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                        RecipeNumbers(p)
                        Text("وصفة بداية من BunnList", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipeNumbers(p: BrewPreset) {
    val items = listOfNotNull(
        "${Methods.grams(p.doseGrams)}غ" to "بن",
        p.hotWaterGrams?.let { "${Methods.grams(it)}غ" to "ماء" },
        p.iceGrams?.let { "${Methods.grams(it)}غ" to "ثلج" },
        p.yieldGrams?.let { "${Methods.grams(it)}غ" to "الناتج" },
        "${Methods.grams(p.waterTempC)}°" to "الحرارة",
        p.ratioText?.let { it to "النسبة" },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items.forEach { (v, k) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(v, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

