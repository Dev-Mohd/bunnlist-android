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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput

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

/** The land outline (Natural Earth 110m, public domain), loaded once from assets as lon/lat rings. */
private object WorldLand {
    @Volatile private var rings: List<FloatArray>? = null
    fun load(context: android.content.Context): List<FloatArray> = rings ?: synchronized(this) {
        rings ?: context.assets.open("world_land.txt").bufferedReader().readLines().map { line ->
            val parts = line.split(' ')
            FloatArray(parts.size * 2).also { arr -> parts.forEachIndexed { i, p -> val (x, y) = p.split(','); arr[i * 2] = x.toFloat(); arr[i * 2 + 1] = y.toFloat() } }
        }.also { rings = it }
    }
}

/** «خريطة المناطق» (iOS OriginsMapView): a world map you can pinch and drag, a bubble per country (bigger with more coffees,
 *  green once tried), and a sheet with the country's numbers, taste and regions. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OriginsMapScreen(app: BunnListApp, nav: Nav) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var data by remember { mutableStateOf<com.bunnlist.core.OriginList?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<OriginInfo?>(null) }
    val land = remember { WorldLand.load(context) }
    LaunchedEffect(reload) {
        failed = false
        try { data = app.api.originsPage(if (app.session.isSignedIn) runCatching { app.session.accessToken() }.getOrNull() else null) } catch (e: ApiError) { failed = data == null }
    }
    val appear by animateFloatAsState(if (data != null) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 200f), label = "appear")
    // Viewport: centre (lon, lat) and zoom; at zoom 1 about 200° of longitude fill the width, as on iOS.
    var cLon by remember { mutableFloatStateOf(-12f) }
    var cLat by remember { mutableFloatStateOf(8f) }
    var zoom by remember { mutableFloatStateOf(1.4f) }
    val ocean = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF1E2A30) else Color(0xFFDCE7EA)
    val landColor = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF3A3530) else Color(0xFFF4EEE3)
    val coast = landColor.copy(alpha = 1f).let { Brand.Brown.copy(alpha = 0.25f) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("خريطة المناطق", onBack = { nav.pop() })
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            BoxWithConstraints(Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)).background(ocean)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gesture, _ ->
                        val k = size.width / 200f * zoom
                        zoom = (zoom * gesture).coerceIn(1f, 6f)
                        cLon = (cLon - pan.x / k).coerceIn(-170f, 170f)
                        cLat = (cLat + pan.y / k).coerceIn(-50f, 65f)
                    }
                }) {
                val wPx = constraints.maxWidth.toFloat(); val hPx = constraints.maxHeight.toFloat()
                val k = wPx / 200f * zoom
                fun px(lon: Float) = (lon - cLon) * k + wPx / 2
                fun py(lat: Float) = (cLat - lat) * k + hPx / 2
                Canvas(Modifier.fillMaxSize()) {
                    // The coffee belt between the tropics.
                    drawRect(Brand.Gold.copy(alpha = 0.10f), Offset(0f, py(23.4f)), androidx.compose.ui.geometry.Size(size.width, py(-23.4f) - py(23.4f)))
                    land.forEach { ring ->
                        val path = androidx.compose.ui.graphics.Path()
                        var i = 0
                        while (i < ring.size) { val x = px(ring[i]); val y = py(ring[i + 1]); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y); i += 2 }
                        path.close()
                        drawPath(path, landColor)
                        drawPath(path, coast, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
                    }
                    for (lat in listOf(23.4f, -23.4f)) drawLine(Brand.Gold.copy(alpha = 0.35f), Offset(0f, py(lat)), Offset(size.width, py(lat)), 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
                }
                val density = androidx.compose.ui.platform.LocalDensity.current
                data?.origins?.filter { it.code in centres }?.sortedBy { it.lots }?.forEach { o ->
                    val (lat, lon) = centres.getValue(o.code)
                    val tried = (o.tried ?: 0) > 0
                    val d = minOf(46f, 20f + sqrt(o.lots.toFloat()) * 2.2f) * (0.85f + 0.15f * zoom.coerceAtMost(2f))
                    val isSel = selected?.code == o.code
                    val bob by animateFloatAsState(if (isSel) 1.15f else 1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f), label = "b${o.code}")
                    val cx = px(lon.toFloat()); val cy = py(lat.toFloat())
                    if (cx < -60 || cx > wPx + 60 || cy < -60 || cy > hPx + 60) return@forEach
                    Column(Modifier.offset { androidx.compose.ui.unit.IntOffset((cx - with(density) { 40.dp.toPx() }).toInt(), (cy - with(density) { (d / 2 + 5).dp.toPx() }).toInt()) }
                        .width(80.dp).graphicsLayer { scaleX = appear * bob; scaleY = appear * bob }
                        .pressable { selected = o }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size((d + 10).dp), contentAlignment = Alignment.Center) {
                            Box(Modifier.fillMaxSize().clip(CircleShape).background((if (tried) Brand.Olive else Brand.Gold).copy(alpha = 0.25f)))
                            Box(Modifier.size(d.dp).androidxShadow().clip(CircleShape).background(if (tried) Brand.Olive else Brand.Gold), contentAlignment = Alignment.Center) {
                                Text(if (tried) "✓" else "${o.lots}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = (d * 0.32f).sp)
                            }
                        }
                        Text(o.nameAr, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Brand.Ink, maxLines = 1,
                            modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                // Header card (iOS): progress line and legend.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    data?.let { dl ->
                        Column(Modifier.align(Alignment.TopCenter).padding(12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val all = dl.origins.count { it.code in centres }
                            val t = dl.triedCountries
                            Text(if (t != null) "جرّبت من $t ${if (t == 1) "دولة" else "دول"} من $all" else "قهاوي من $all دولة", fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                listOf(Brand.Olive to "جرّبت منها", Brand.Gold to "لسّا ما جرّبت").forEach { (c, l) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(c)); Text(" $l", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            Text("اسحب وكبّر بإصبعين، واضغط على أي دولة.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    when {
                        data == null && failed -> Box(Modifier.align(Alignment.Center).padding(40.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface)) {
                            Retry("تعذّر التحميل") { reload++ }
                        }
                        data == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    }
                }
            }
        }
    }
    selected?.let { o ->
        ModalBottomSheet(onDismissRequest = { selected = null }) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(o.nameAr, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    o.flag?.let { Text(" $it", fontSize = 24.sp) }
                    Spacer(Modifier.weight(1f))
                    if ((o.tried ?: 0) > 0) Text("✓ جرّبت ${o.tried}", fontWeight = FontWeight.Bold, color = Tone.olive)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("${o.lots}" to "قهوة", "${o.roasters}" to "محمصة",
                        (if (o.averageRating > 0) String.format(java.util.Locale.ROOT, "%.1f", o.averageRating) else "—") to "التقييم").forEach { (v, l) ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(v, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(l, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val fams = o.families.mapNotNull { k -> FlavorFamily.entries.firstOrNull { it.code == k.key } }
                if (fams.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("طعمها غالبًا", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        fams.forEach { f -> Text(f.shortNameAr, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(CircleShape).background(f.color()).padding(horizontal = 12.dp, vertical = 6.dp)) }
                    }
                }
                if (o.processes.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المعالجة", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        o.processes.forEach { k -> Text(com.bunnlist.core.Methods.processAr(k.key) ?: k.key, fontSize = 13.sp,
                            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp)) }
                    }
                }
                if (o.regions.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المناطق", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        o.regions.forEach { r -> Text(r, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 6.dp)) }
                    }
                }
                Button(onClick = { selected = null; nav.openOrigin(o.code) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = CircleShape) {
                    Text("شوف قهاويها (${o.lots})", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun Modifier.androidxShadow() = this.then(Modifier.graphicsLayer { shadowElevation = 6f; shape = CircleShape; clip = false })

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
