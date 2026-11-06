package com.bunnlist.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.CollectionFlags
import com.bunnlist.core.MyReview
import kotlinx.coroutines.launch
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.Methods
import com.bunnlist.core.RemoteLotDetail
import com.bunnlist.core.RemoteRoasterRecipe

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LotScreen(app: BunnListApp, slug: String, nav: Nav) {
    var lot by remember(slug) { mutableStateOf<RemoteLotDetail?>(null) }
    var failed by remember(slug) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(slug, attempt) {
        failed = false
        try { lot = app.api.lot(slug) } catch (e: ApiError) { failed = true }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopBar(lot?.nameAr ?: "", onBack = { nav.pop() })
        val l = lot
        when {
            l != null -> LotBody(app, l, nav) { lot = it }
            failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ما قدرنا نجيب القهوة"); TextButton(onClick = { attempt++ }) { Text("إعادة المحاولة") }
                }
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LotBody(app: BunnListApp, lot: RemoteLotDetail, nav: Nav, onUpdated: (RemoteLotDetail) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn = app.session.isSignedIn
    var flags by remember(lot.slug) { mutableStateOf<CollectionFlags?>(null) }
    var savingFlags by remember { mutableStateOf(false) }
    var myReview by remember(lot.slug) { mutableStateOf<MyReview?>(null) }
    var reviewing by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(lot.slug, signedIn) {
        if (!signedIn) return@LaunchedEffect
        runCatching { app.session.withToken { t -> flags = app.api.collectionFlags(lot.slug, t); myReview = app.api.myReview(lot.slug, t) } }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(model = lot.imageUrl, contentDescription = lot.nameAr, contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(24.dp)).background(Color.White))
        Column {
            Text(lot.nameAr, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            if (lot.name.isNotBlank() && lot.name != lot.nameAr) Text(lot.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            Text(lot.roasterNameAr, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            if (lot.tags.any { it.tag == "luxury" }) {
                Spacer(Modifier.height(6.dp))
                AssistChip(onClick = {}, label = { Text("فاخرة ✦") }, colors = AssistChipDefaults.assistChipColors(labelColor = MaterialTheme.colorScheme.secondary))
            }
        }
        if (lot.reviewCount > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary)
            Text(" ${String.format(java.util.Locale.ROOT, "%.1f", lot.averageRating)}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("  ${lot.reviewCount} تقييم", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (signedIn) {
            val f = flags ?: CollectionFlags()
            CollectionToggles(f, busy = savingFlags || flags == null) { next ->
                val before = flags; flags = next; savingFlags = true
                scope.launch {
                    try { flags = app.session.withToken { app.api.setCollection(lot.slug, next, it) }; note = null }
                    catch (e: ApiError) { flags = before; note = "ما انحفظ، جرّب مرة ثانية." }
                    savingFlags = false
                }
            }
            OutlinedButton(onClick = { reviewing = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50)) {
                Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary)
                Text(myReview?.let { "  تقييمك: ${it.rating} من 5 · عدّله" } ?: "  قيّمها")
            }
            note?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }
        } else {
            Text("سجّل دخولك عشان تحفظها في مجموعتك وتقيّمها.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (lot.flavorNotes.isNotEmpty()) {
            SectionTitle("النكهات")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lot.flavorNotes.forEach { note ->
                    val c = Color(0xFF000000 or FlavorFamily.of(note).rgb.toLong())
                    Row(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.15f)).padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(c))
                        Text("  $note", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        val facts = listOfNotNull(
            lot.originNameAr?.let { "المنشأ" to listOfNotNull(lot.flag, it, lot.regionAr).joinToString(" ") },
            lot.altitudeMeters?.takeIf { it > 0 }?.let { "الارتفاع" to "$it م" },
            lot.processAr?.let { "المعالجة" to it },
            lot.variety?.takeIf { it.isNotBlank() }?.let { "السلالة" to it },
            lot.roastLevel?.let { "التحميص" to it },
        )
        if (facts.isNotEmpty()) {
            SectionTitle("عن القهوة")
            facts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (k, v) -> FactTile(k, v, Modifier.weight(1f)) }
                }
            }
        }
        lot.descriptionAr?.takeIf { it.isNotBlank() }?.let { Text(it, lineHeight = 24.sp) }

        if (lot.recommendedBrewMethods.isNotEmpty()) {
            SectionTitle("حضّرها")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lot.recommendedBrewMethods.forEach { m ->
                    FilledTonalButton(onClick = { nav.openBrewTab(m, lot.nameAr) }) { Text(Methods.nameAr(m)) }
                }
            }
        }

        if (lot.roasterRecipes.isNotEmpty()) {
            SectionTitle("وصفة المحمصة")
            lot.roasterRecipes.forEach { RoasterRecipeCard(it) }
        }

        lot.community?.takeIf { it.collectors + it.brewers > 0 }?.let { c ->
            SectionTitle("المجتمع")
            Text("${c.collectors} عندهم بالمجموعة · ${c.brewers} حضّروها", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        lot.buy?.let { buy ->
            Card(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buy.url))) }, shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ShoppingBag, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(buy.title, fontWeight = FontWeight.Bold)
                        Text(buy.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (reviewing) ReviewSheet(myReview, lot.recommendedBrewMethods.firstOrNull(), onDismiss = { reviewing = false }) { r ->
        try {
            val result = app.session.withToken { app.api.submitReview(lot.slug, r, it) }
            myReview = result.review
            onUpdated(lot.copy(averageRating = result.averageRating, reviewCount = result.reviewCount))
            flags?.let { f -> if (!f.tried) runCatching { flags = app.session.withToken { t -> app.api.setCollection(lot.slug, f.copy(tried = true, wantsToTry = false), t) } } }
            reviewing = false; note = "انحفظ تقييمك، شكرًا لك!"
            null
        } catch (e: ApiError) {
            when (e) { ApiError.Forbidden -> "حسابك موقوف عن النشر حاليًا."; ApiError.RateLimited -> "محاولات كثيرة، جرّب بعد شوي."; else -> "ما انحفظ، جرّب مرة ثانية." }
        }
    }
}

@Composable
private fun FactTile(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(14.dp)) {
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun RoasterRecipeCard(r: RemoteRoasterRecipe) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(r.label, fontWeight = FontWeight.Bold)
            val numbers = listOfNotNull(
                r.doseG?.let { "${Methods.grams(it)}غ بن" }, r.waterMl?.let { "$it مل ماء" },
                r.tempC?.let { "$it°م" }, r.timeSec?.let { Methods.clock(it) }, r.grind,
            )
            if (numbers.isNotEmpty()) Text(numbers.joinToString(" · "), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            r.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", fontSize = 14.sp) }
            r.link?.let { link -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }) { Text("افتح الوصفة") } }
        }
    }
}
