package com.bunnlist.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.OriginInfo
import com.bunnlist.core.RoasterCard
import kotlin.math.sqrt

/** «المحامص»: every roaster with a few of its bags; opening one shows its coffees. */
@Composable
fun RoastersScreen(app: BunnListApp, nav: Nav) {
    var roasters by remember { mutableStateOf<List<RoasterCard>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var q by remember { mutableStateOf("") }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { failed = false; try { roasters = app.api.roasters() } catch (e: ApiError) { failed = true } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("المحامص", onBack = { nav.pop() })
        OutlinedTextField(q, { q = it }, placeholder = { Text("ابحث عن محمصة") }, singleLine = true, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        val list = roasters?.filter { q.isBlank() || it.nameAr.contains(q.trim()) || (it.name ?: "").contains(q.trim(), ignoreCase = true) }
        when {
            list == null && failed -> Retry("ما قدرنا نجيب المحامص") { reload++ }
            list == null -> Loading()
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("${list.size} محمصة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
                items(list, key = { it.slug }) { r ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface).pressable { nav.openRoaster(r.slug, r.nameAr) }.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                                AsyncImage(r.logoUrl, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(4.dp))
                            }
                            Column(Modifier.padding(start = 12.dp).weight(1f)) {
                                Text(r.nameAr, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(listOfNotNull(r.cityAr, "${r.lots} محصول").joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (r.images.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            r.images.take(4).forEach { img -> LotPhotoWell(img, emptyList(), Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp)), 4.dp) }
                            repeat(4 - r.images.take(4).size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}

/** Rough centres of coffee countries, for placing them on the map. */
private val centres: Map<String, Pair<Double, Double>> = mapOf(
    "CO" to (4.6 to -74.1), "ET" to (9.0 to 39.5), "BR" to (-14.2 to -51.9), "CR" to (9.7 to -84.0), "SV" to (13.8 to -88.9), "YE" to (15.5 to 44.2),
    "UG" to (1.4 to 32.3), "KE" to (0.0 to 37.9), "RW" to (-1.9 to 29.9), "BI" to (-3.4 to 29.9), "TZ" to (-6.4 to 34.9), "PE" to (-9.2 to -75.0),
    "HN" to (15.2 to -86.2), "GT" to (15.8 to -90.2), "NI" to (12.9 to -85.2), "PA" to (8.5 to -80.8), "MX" to (23.6 to -102.5), "EC" to (-1.8 to -78.2),
    "BO" to (-16.3 to -63.6), "ID" to (-2.5 to 118.0), "IN" to (20.6 to 79.0), "CN" to (24.5 to 101.0), "VN" to (14.1 to 108.3), "TH" to (15.9 to 100.9),
    "MM" to (21.9 to 95.9), "LA" to (19.9 to 102.5), "PG" to (-6.3 to 143.9), "SA" to (19.0 to 42.0), "JM" to (18.1 to -77.3), "HT" to (19.0 to -72.3),
    "DO" to (18.7 to -70.2), "CU" to (21.5 to -77.8), "CD" to (-4.0 to 21.8), "CM" to (7.4 to 12.4), "MW" to (-13.3 to 34.3), "ZM" to (-13.1 to 27.8),
    "ZW" to (-19.0 to 29.2), "TL" to (-8.9 to 125.7), "PH" to (12.9 to 121.8), "US" to (19.6 to -155.5), "TW" to (23.7 to 121.0), "SR" to (4.0 to -56.0),
)

/** «خريطة المناطق»: where the coffees come from, on the coffee belt between the tropics. */
@Composable
fun OriginsMapScreen(app: BunnListApp, nav: Nav) {
    var origins by remember { mutableStateOf<List<OriginInfo>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var picked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(reload) { failed = false; try { origins = app.api.origins(null) } catch (e: ApiError) { failed = true } }
    val grow by animateFloatAsState(if (origins != null) 1f else 0f, tween(900), label = "grow")
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("خريطة المناطق", onBack = { nav.pop() })
        val list = origins?.filter { it.code in centres }
        when {
            list == null && failed -> Retry("ما قدرنا نجيب المناطق") { reload++ }
            list == null -> Loading()
            else -> LazyColumn(contentPadding = PaddingValues(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Text("قهاوي من ${list.size} دولة", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("كل نقطة دولة، وحجمها على قد عدد محاصيلها. الشريط هو حزام القهوة بين المدارين.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    val max = list.maxOf { it.lots }.coerceAtLeast(1)
                    val gold = Brand.Gold; val line = MaterialTheme.colorScheme.outline; val sel = MaterialTheme.colorScheme.primary
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Canvas(Modifier.padding(horizontal = 16.dp).fillMaxWidth().aspectRatio(2f).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surface)) {
                            fun x(lon: Double) = ((lon + 180) / 360 * size.width).toFloat()
                            fun y(lat: Double) = ((60 - lat) / 110 * size.height).toFloat() // 60°N … 50°S
                            drawRect(gold.copy(alpha = 0.10f), Offset(0f, y(23.4)), androidx.compose.ui.geometry.Size(size.width, y(-23.4) - y(23.4)))
                            for (lat in listOf(23.4, 0.0, -23.4)) drawLine(line, Offset(0f, y(lat)), Offset(size.width, y(lat)), 1.5f,
                                pathEffect = if (lat == 0.0) null else PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
                            for (lon in -150..150 step 30) drawLine(line.copy(alpha = 0.4f), Offset(x(lon.toDouble()), 0f), Offset(x(lon.toDouble()), size.height), 1f)
                            list.forEach { o ->
                                val (lat, lon) = centres.getValue(o.code)
                                val r = (5 + 16 * sqrt(o.lots.toDouble() / max)).toFloat() * grow * density / 2.6f
                                val c = if (picked == o.code) sel else gold
                                drawCircle(c.copy(alpha = 0.25f), r * 1.6f, Offset(x(lon), y(lat)))
                                drawCircle(c, r, Offset(x(lon), y(lat)))
                            }
                        }
                    }
                }
                items(list.sortedByDescending { it.lots }, key = { it.code }) { o ->
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                        .background(if (picked == o.code) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                        .pressable { picked = o.code; nav.openOrigin(o.code) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(o.flag ?: "", fontSize = 30.sp)
                        Column(Modifier.padding(start = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(o.nameAr, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${o.lots} محصول · ${o.roasters} محمصة", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (o.regions.isNotEmpty()) Text(o.regions.take(4).joinToString("، "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                o.families.take(3).forEach { k ->
                                    FlavorFamily.entries.firstOrNull { it.code == k.key }?.let { f ->
                                        Text(f.shortNameAr, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Brand.Brown,
                                            modifier = Modifier.clip(CircleShape).background(f.color().copy(alpha = 0.16f)).padding(horizontal = 7.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A roaster's page (iOS RoasterPageView): its bags fanned behind the badge, numbers, follow, its taste, origins, rows and every coffee. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoasterScreen(app: BunnListApp, slug: String, nav: Nav) {
    var page by remember { mutableStateOf<com.bunnlist.core.RoasterPage?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var showAll by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val signedIn = app.session.isSignedIn
    LaunchedEffect(reload) {
        failed = false
        try { page = app.api.roaster(slug, if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null) } catch (e: ApiError) { failed = true }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar(page?.roaster?.nameAr ?: "", onBack = { nav.pop() })
        val p = page
        when {
            p == null && failed -> Retry("تعذّر التحميل") { reload++ }
            p == null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth().height(200.dp)); SkeletonBlock(Modifier.fillMaxWidth().height(70.dp)); SkeletonBlock(Modifier.fillMaxWidth().height(120.dp))
            }
            else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val full = androidx.compose.foundation.lazy.grid.GridItemSpan(2)
                val r = p.roaster
                item(span = { full }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.fillMaxWidth().height(176.dp), contentAlignment = Alignment.TopCenter) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                Row(horizontalArrangement = Arrangement.spacedBy((-30).dp)) {
                                    r.images.take(3).forEachIndexed { i, url ->
                                        AsyncImage(url, null, contentScale = ContentScale.Fit, modifier = Modifier.size(92.dp, 120.dp)
                                            .graphicsLayer { rotationZ = (i - 1) * 9f; translationY = (if (i == 1) -8 else 4) * density }.multiply())
                                    }
                                }
                            }
                            Box(Modifier.align(Alignment.BottomCenter).size(76.dp).clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(4.dp).clip(CircleShape).background(Color.White),
                                contentAlignment = Alignment.Center) { AsyncImage(r.logoUrl, r.nameAr, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(6.dp)) }
                        }
                        Text(r.nameAr, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        if (r.name.isNotBlank() && !r.name.equals(r.nameAr, true)) Text(r.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        r.cityAr?.let { Text("📍 $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("${r.lots}" to "قهوة", (if (r.reviews > 0) String.format(java.util.Locale.ROOT, "%.1f", r.averageRating) else "—") to "التقييم", "${r.followers}" to "متابع").forEach { (v, l) ->
                                Column(Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(v, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.tertiary); Text(l, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (signedIn) {
                            val on = p.isFollowing
                            Row(Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary)
                                .pressable(enabled = !busy) {
                                    busy = true
                                    scope.launch { runCatching { app.session.withToken { app.api.setRoasterFollow(slug, !on, it) } }.onSuccess { page = p.copy(isFollowing = !on, roaster = r.copy(followers = (r.followers + if (on) -1 else 1).coerceAtLeast(0))) }; busy = false }
                                }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text(if (on) "✓ تتابعها" else "+ تابع المحمصة", fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
                val shares = p.familyShares
                if (shares.isNotEmpty()) item(span = { full }) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("وش تميل له قهاويها", fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().height(16.dp).clip(CircleShape), horizontalArrangement = Arrangement.spacedBy(2.dp)) { shares.forEach { (f, pct) -> Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(f.color())) } }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            shares.forEach { (f, pct) -> Text("${f.shortNameAr} $pct٪", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = f.color(), modifier = Modifier.clip(CircleShape).background(f.color().copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 4.dp)) }
                        }
                        if (p.processes.isNotEmpty()) Text("المعالجة: " + p.processes.take(3).joinToString("، ") { com.bunnlist.core.Methods.processAr(it.key) ?: it.key }, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (p.origins.isNotEmpty()) item(span = { full }) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("من وين قهاويها", fontWeight = FontWeight.Bold)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            p.origins.forEach { Text("${it.key} · ${it.count}", fontSize = 13.sp, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp)) }
                        }
                    }
                }
                listOf("الأعلى تقييمًا" to p.topRated, "وصلت حديثًا" to (if (p.lots.size > 4) p.newest else emptyList())).forEach { (title, lots) ->
                    if (lots.isNotEmpty()) item(span = { full }) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(title, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { items(lots, key = { title + it.id }) { l -> BunnCoffeeCard(l, Modifier.width(170.dp)) { nav.push(Route.Lot(l.slug)) } } }
                        }
                    }
                }
                item(span = { full }) { Row { Text("كل قهاويها", fontWeight = FontWeight.Bold); Text("  ${p.lots.size}", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                val shown = if (showAll) p.lots else p.lots.take(8)
                items(shown.size, key = { "a" + shown[it].id }) { i -> BunnCoffeeCard(shown[i]) { nav.push(Route.Lot(shown[i].slug)) } }
                if (p.lots.size > 8 && !showAll) item(span = { full }) {
                    Text("اعرض الكل (${p.lots.size})", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                        .pressable { showAll = true }.wrapContentHeight(Alignment.CenterVertically), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
