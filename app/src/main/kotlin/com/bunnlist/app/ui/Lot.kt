package com.bunnlist.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.BrewPreset
import com.bunnlist.core.CollectionFlags
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.Methods
import com.bunnlist.core.MyReview
import com.bunnlist.core.RemoteLotDetail
import com.bunnlist.core.RemoteRoasterRecipe
import kotlinx.coroutines.launch

@Composable
fun LotScreen(app: BunnListApp, slug: String, nav: Nav, embedded: Boolean = false, openReview: Boolean = false) {
    var lot by remember(slug) { mutableStateOf<RemoteLotDetail?>(null) }
    var failed by remember(slug) { mutableStateOf(false) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(slug, attempt) {
        failed = false
        try { lot = app.api.lot(slug) } catch (e: ApiError) { failed = true }
    }
    val l = lot
    when {
        l != null -> LotBody(app, l, nav, embedded, openReview) { lot = it }
        else -> Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!embedded) TopBar("", onBack = { nav.pop() })
            if (failed) Retry("ما قدرنا نجيب القهوة") { attempt++ } else Loading()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun LotBody(app: BunnListApp, lot: RemoteLotDetail, nav: Nav, embedded: Boolean, openReview: Boolean, onUpdated: (RemoteLotDetail) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn = app.session.isSignedIn
    var flags by remember(lot.slug) { mutableStateOf<CollectionFlags?>(null) }
    var myReview by remember(lot.slug) { mutableStateOf<MyReview?>(null) }
    var reviewing by remember(lot.slug) { mutableStateOf(openReview) }
    var choosingMethod by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val tint = rememberBagColor(lot.imageUrl)
    LaunchedEffect(lot.slug, signedIn) {
        if (!signedIn) return@LaunchedEffect
        runCatching { app.session.withToken { t -> flags = app.api.collectionFlags(lot.slug, t); myReview = app.api.myReview(lot.slug, t) } }
    }
    fun save(next: CollectionFlags, done: String? = null) {
        if (!signedIn) { note = "سجّل دخولك عشان تحفظها في مجموعتك."; return }
        val before = flags; flags = next
        scope.launch {
            try { flags = app.session.withToken { app.api.setCollection(lot.slug, next, it) }; note = done }
            catch (e: ApiError) { flags = before; note = "ما انحفظ، جرّب مرة ثانية." }
        }
    }
    val f = flags ?: CollectionFlags()
    val methods = lot.recommendedBrewMethods.filter { it in BrewPreset.methods }.ifEmpty { listOf("V60", "ESPRESSO") }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .background(Brush.verticalGradient(0f to (tint ?: Color.Transparent).copy(alpha = 0.22f), 0.45f to Color.Transparent))) {
        // Back on the right (RTL), save and share on the left; the rest of the collection lives in one menu.
        Row(Modifier.fillMaxWidth().then(if (embedded) Modifier else Modifier.statusBarsPadding()).padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (!embedded) IconButton(onClick = { nav.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "رجوع") }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { save(f.copy(wantsToTry = !f.wantsToTry, tried = if (!f.wantsToTry) false else f.tried), if (!f.wantsToTry) "انحفظت في «أبي أجربها»." else null) }) {
                Icon(if (f.wantsToTry) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (f.wantsToTry) "محفوظة" else "احفظها",
                    tint = if (f.wantsToTry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground)
            }
            IconButton(onClick = {
                val text = "${lot.nameAr} من ${lot.roasterNameAr}\nhttps://bunnlist.com/coffees/${lot.slug}"
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
            }) { Icon(Icons.Filled.Share, "مشاركة") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    @Composable fun item(label: String, on: Boolean, next: CollectionFlags) =
                        DropdownMenuItem(text = { Text(label) }, onClick = { menu = false; save(next) },
                            leadingIcon = { if (on) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) else Spacer(Modifier.size(24.dp)) })
                    item("عندي الكيس", f.owned, f.copy(owned = !f.owned))
                    item("مفضلة", f.favorite, f.copy(favorite = !f.favorite))
                    item("جرّبتها", f.tried, f.copy(tried = !f.tried, wantsToTry = if (!f.tried) false else f.wantsToTry))
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(if (myReview != null) "عدّل تقييمك" else "قيّمها") }, onClick = { menu = false; if (signedIn) reviewing = true else note = "سجّل دخولك عشان تقيّمها." },
                        leadingIcon = { Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary) })
                }
            }
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // The bag centred on cream, its notes underneath so a printed label stays readable.
            Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp)).background(Brand.Cream), contentAlignment = Alignment.Center) {
                AsyncImage(lot.imageUrl, lot.nameAr, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
            }
            if (lot.flavorNotes.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lot.flavorNotes.forEach { n ->
                    val c = Color(0xFF000000 or FlavorFamily.of(n).rgb.toLong())
                    Row(Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.18f)).padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(c)); Text("  $n", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(lot.nameAr, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                if (lot.name.isNotBlank() && lot.name != lot.nameAr) Text(lot.name, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                val roasterLink = lot.roasterSlug
                Text(lot.roasterNameAr, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                    textDecoration = if (roasterLink != null) TextDecoration.Underline else null,
                    modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)
                        .then(if (roasterLink != null) Modifier.clickable { nav.openRoaster(roasterLink, lot.roasterNameAr) } else Modifier))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lot.tags.any { it.tag == "luxury" }) Text("فاخرة ✦  ", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                    if (lot.reviewCount > 0) {
                        Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Text(" ${String.format(java.util.Locale.ROOT, "%.1f", lot.averageRating)}", fontWeight = FontWeight.Bold)
                        Text("  ${lot.reviewCount} تقييم", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                lot.originNameAr?.let { FactTile("المنشأ", listOfNotNull(lot.flag, it).joinToString(" "), Modifier.weight(1f)) }
                lot.processAr?.let { FactTile("المعالجة", it, Modifier.weight(1f)) }
            }

            // The next step depends on where you are with this coffee.
            when {
                f.owned -> {
                    Button(onClick = { choosingMethod = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                        Text("حضّر هالقهوة", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                    if (myReview == null) OutlinedButton(onClick = { reviewing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(18.dp)) { Text("كيف كان كوبك؟ قيّمها") }
                }
                lot.buy != null -> {
                    val buy = lot.buy!!
                    Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(buy.url))) }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Filled.ShoppingBag, null)
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(buy.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(buy.host, fontSize = 12.sp)
                        }
                    }
                    if (signedIn) TextButton(onClick = { save(f.copy(owned = true), "انضافت لـ«عندي». حضّرها متى ما تبي.") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("عندي الكيس") }
                }
                else -> if (signedIn) OutlinedButton(onClick = { save(f.copy(owned = true), "انضافت لـ«عندي».") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(18.dp)) { Text("عندي الكيس") }
            }
            myReview?.let { r -> Text("تقييمك: ${"★".repeat(r.rating)}${"☆".repeat(5 - r.rating)} · ${Methods.nameAr(r.brewMethod)}", color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.clickable { reviewing = true }.heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically)) }
            note?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp) }

            val more = listOfNotNull(
                lot.variety?.takeIf { it.isNotBlank() }?.let { "السلالة" to it },
                lot.altitudeMeters?.takeIf { it > 0 }?.let { "الارتفاع" to "$it م" },
                lot.roastLevel?.let { "التحميص" to it },
                lot.regionAr?.let { "المنطقة" to it },
            )
            if (more.isNotEmpty()) {
                SectionTitle("عن القهوة")
                more.chunked(2).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { row.forEach { (k, v) -> FactTile(k, v, Modifier.weight(1f)) } } }
            }
            lot.descriptionAr?.takeIf { it.isNotBlank() }?.let { Text(it, lineHeight = 24.sp) }
            if (lot.roasterRecipes.isNotEmpty()) {
                SectionTitle("وصفة المحمصة")
                lot.roasterRecipes.forEach { RoasterRecipeCard(it) }
            }
            lot.community?.takeIf { it.collectors + it.brewers > 0 }?.let { c ->
                SectionTitle("المجتمع")
                Text("${c.collectors} عندهم بالمجموعة · ${c.brewers} حضّروها", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!f.owned && lot.buy == null && !signedIn) Text("سجّل دخولك عشان تحفظها وتقيّمها.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (choosingMethod) ModalBottomSheet(onDismissRequest = { choosingMethod = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("بأي طريقة؟", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            methods.forEach { m ->
                val preset = BrewPreset.presets(m).first()
                Card(onClick = { choosingMethod = false; nav.push(Route.Brewing(preset, lot.nameAr, lot.slug)) }, shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(Methods.nameAr(m), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${preset.nameAr} · ${preset.summaryAr}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            TextButton(onClick = { choosingMethod = false; nav.openBrewTab(methods.first(), lot.nameAr) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("كل الطرق والوصفات") }
        }
    }

    if (reviewing) ReviewSheet(myReview, lot.recommendedBrewMethods.firstOrNull(), onDismiss = { reviewing = false }) { r ->
        try {
            val result = app.session.withToken { app.api.submitReview(lot.slug, r, it) }
            myReview = result.review
            onUpdated(lot.copy(averageRating = result.averageRating, reviewCount = result.reviewCount))
            flags?.let { cur -> if (!cur.tried) runCatching { flags = app.session.withToken { t -> app.api.setCollection(lot.slug, cur.copy(tried = true, wantsToTry = false), t) } } }
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
            val numbers = listOfNotNull(r.doseG?.let { "${Methods.grams(it)}غ بن" }, r.waterMl?.let { "$it مل ماء" }, r.tempC?.let { "$it°م" }, r.timeSec?.let { Methods.clock(it) }, r.grind)
            if (numbers.isNotEmpty()) Text(numbers.joinToString(" · "), color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
            r.steps.forEachIndexed { i, s -> Text("${i + 1}. $s", fontSize = 14.sp) }
            r.link?.let { link -> TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }) { Text("افتح الوصفة") } }
        }
    }
}
