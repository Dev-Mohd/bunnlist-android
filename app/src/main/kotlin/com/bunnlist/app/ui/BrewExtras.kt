package com.bunnlist.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.BrewTaste
import com.bunnlist.core.Methods
import com.bunnlist.core.Passport
import com.bunnlist.core.RemoteRecipe
import com.bunnlist.core.TasteFix
import kotlinx.coroutines.launch
import java.util.Locale

// MARK: «كيف طلعت؟»

/**
 * After a brew (as iOS BrewFinishView): optional. With stars it saves a brew experience — private unless
 * published — with the brew log; picking a taste shows one change to try next time.
 */
@Composable
fun BrewFinishScreen(app: BunnListApp, r: Route.BrewFinish, nav: Nav) {
    var stars by remember { mutableIntStateOf(0) }
    var taste by remember { mutableStateOf<BrewTaste?>(null) }
    var note by remember { mutableStateOf("") }
    var publish by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val p = r.preset
    // One tap: a star saves the brew straight away. The taste, the note and publishing update the same record.
    val clientId = remember { java.util.UUID.randomUUID().toString() }
    val brewedAt = remember { java.time.Instant.now().toString() }
    var savedOnce by remember { mutableStateOf(false) }
    var dirty by remember { mutableStateOf(false) }
    fun save(done: Boolean = false) {
        if (stars == 0) return
        if (saving) { dirty = true; if (done) saved = true; return }
        saving = true; error = null
        scope.launch {
            var ok: Boolean
            do {
                dirty = false
                ok = try {
                    app.session.withToken { app.api.saveExperience(r.lotSlug, p, stars, r.seconds, taste, note, publish, it, clientId, brewedAt) }
                    true
                } catch (e: ApiError) {
                    error = when (e) { ApiError.Forbidden -> "حسابك موقوف عن النشر حاليًا."; is ApiError.Network -> "تأكد من اتصالك بالإنترنت."; else -> "ما انحفظت، جرّب مرة ثانية." }
                    false
                }
            } while (dirty && ok)
            saving = false
            if (ok) { if (!savedOnce) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM); savedOnce = true; if (done) saved = true }
            else if (done) saved = false
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!saved && stars == 0) TextButton(onClick = { nav.pop() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("تخطّي") } else Spacer(Modifier.width(64.dp))
            Text("كيف طلعت؟", fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(64.dp))
        }
        if (saved) Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
            val pop by animateFloatAsState(1f, spring(dampingRatio = 0.45f), label = "saved")
            Icon(Icons.Filled.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp).graphicsLayer { scaleX = pop; scaleY = pop })
            Text("انحفظت تجربتك", fontWeight = FontWeight.Bold, fontSize = 22.sp)
            Text(if (publish) "نشرناها في المجتمع، ويقدر الناس يعلّقون عليها." else "خاصة لك. تقدر تنشرها بعدين.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = { nav.rateLot(r.lotSlug) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)) { Text("قيّم القهوة نفسها", fontWeight = FontWeight.Bold) }
            if (p.source != "mine") KeepRecipeCard { nav.pop(); nav.push(Route.RecipeEditor(keepDraft(p, r.lotSlug, r.lotName))) }
            TextButton(onClick = { nav.pop() }) { Text("تمام") }
        } else Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.lotName ?: p.nameAr, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(listOfNotNull(Methods.nameAr(p.methodCode), r.seconds.takeIf { it > 0 }?.let { Methods.clock(it) }).joinToString(" · "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                        (1..5).forEach { n ->
                            val on = n <= stars
                            val sc by animateFloatAsState(if (on) 1.12f else 1f, spring(dampingRatio = 0.45f), label = "star$n")
                            Icon(if (on) Icons.Filled.Star else Icons.Filled.StarBorder, "$n نجوم", tint = if (on) Brand.Gold else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp).graphicsLayer { scaleX = sc; scaleY = sc }.pressable { stars = n; view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK); save() })
                        }
                    }
                }
                if (savedOnce) Text("✓ انحفظت", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                else if (stars == 0) Text("اضغط نجمة وتنحفظ على طول", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("الطعم بكلمة", fontWeight = FontWeight.Bold)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrewTaste.entries.forEach { t ->
                        val on = taste == t
                        Column(Modifier.clip(RoundedCornerShape(16.dp)).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .pressable { taste = if (on) null else t; save() }.padding(horizontal = 14.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(t.nameAr, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            Text(t.hintAr, fontSize = 11.sp, color = if (on) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                AnimatedVisibility(taste != null, enter = expandVertically() + fadeIn()) {
                    taste?.let { t ->
                        val fix = TasteFix.suggest(t, p.methodCode, p.doseGrams, if (p.methodCode == "ESPRESSO") p.yieldGrams else p.hotWaterGrams)
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brand.Gold.copy(alpha = 0.14f)).padding(14.dp)) {
                            Icon(Icons.Filled.Lightbulb, null, tint = Brand.Gold)
                            Column(Modifier.padding(start = 10.dp)) {
                                Text("المرة الجاية: ${fix.titleAr}", fontWeight = FontWeight.Bold)
                                Text(fix.detailAr, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            OutlinedTextField(note, { note = it.take(2000) }, placeholder = { Text("ملاحظة لنفسك (اختياري)") }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), shape = RoundedCornerShape(16.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("انشرها في المجتمع", fontWeight = FontWeight.SemiBold); Text("تطلع للناس مع وصفتك وأرقامك", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(publish, { publish = it })
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            // The note and the publish switch go in with this last save.
            Button(enabled = stars > 0, onClick = { save(done = true) }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)) {
                Text(if (stars == 0) "اختر النجوم أول" else "تم", fontWeight = FontWeight.Bold, fontSize = 17.sp)
            }
        }
    }
}


/** «احفظها في وصفاتي»: the recipe as brewed (at the chosen cups), opened in the editor to name and save. */
@Composable
internal fun KeepRecipeCard(onKeep: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.primary).padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("طلعت حلوة؟ 👌", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimary)
        Text("احفظ هالوصفة بأرقامك عشان ترجع لها", fontSize = 13.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f))
        Text("احفظها في وصفاتي", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.onPrimary).pressable(onClick = onKeep).padding(horizontal = 14.dp, vertical = 7.dp))
    }
}

internal fun keepDraft(p: com.bunnlist.core.BrewPreset, lotSlug: String?, lotName: String?) =
    com.bunnlist.core.RecipeDraft.from(p, lotSlug, lotName).copy(title = "", sourceId = p.recipeId)

// MARK: Recipes

/** «بار التحضير»: your recipes and the community's for this coffee; any of them runs in the guided brew. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipesScreen(app: BunnListApp, lotSlug: String?, lotName: String?, nav: Nav, openId: String? = null) {
    var tab by remember { mutableIntStateOf(1) }
    var mine by remember { mutableStateOf<List<RemoteRecipe>?>(null) }
    var explore by remember { mutableStateOf<List<RemoteRecipe>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var open by remember { mutableStateOf<RemoteRecipe?>(null) }
    val signedIn = app.session.isSignedIn
    LaunchedEffect(reload, nav.version) {
        failed = false
        try {
            val token = if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null
            explore = app.api.exploreRecipes(lotSlug, token)
            if (openId != null && open == null) open = explore?.firstOrNull { it.id == openId }
            if (token != null) mine = app.api.myRecipes(token).filter { lotSlug == null || it.lot?.slug == lotSlug || it.lot == null }
        } catch (e: ApiError) { failed = true }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("بار التحضير", onBack = { nav.pop() }) {
            if (signedIn) IconButton(onClick = { nav.push(Route.RecipeEditor(com.bunnlist.core.RecipeDraft.from(com.bunnlist.core.BrewPreset.all.first(), lotSlug))) }) {
                Icon(Icons.Filled.Add, "وصفة جديدة")
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("وصفاتي", if (lotSlug == null) "استكشف" else "وصفات هذه القهوة").forEachIndexed { i, l ->
                SegmentedButton(selected = tab == i, onClick = { tab = i }, shape = SegmentedButtonDefaults.itemShape(i, 2)) { Text(l) }
            }
        }
        val list = if (tab == 0) mine else explore
        when {
            tab == 0 && !signedIn -> Empty("سجّل الدخول لوصفاتك", "وصفاتك تُحفظ في حسابك وتظهر على كل أجهزتك.")
            list == null && failed -> Retry("تعذّر تحميل الوصفات") { reload++ }
            list == null -> Loading()
            list.isEmpty() -> Empty(if (tab == 0) "بار التحضير فاضي" else "لا وصفات منشورة بعد",
                if (tab == 0) "كل باريستا له توقيعه — اضغط + واكتب أول وصفة لك." else if (lotSlug == null) "انشر وصفتك لتكون أول وصفة هنا." else "كن أول من ينشر وصفة لهذه القهوة.")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(list, key = { it.id }) { rc ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).pressable { open = rc }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Coffee, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(rc.title.ifBlank { "وصفة" }, fontWeight = FontWeight.Bold)
                            Text(listOfNotNull(Methods.nameAr(rc.methodCode), rc.doseGrams?.let { "${Methods.grams(it)}غ" }, if (tab == 1) rc.authorName.takeIf { it.isNotBlank() } else rc.lot?.nameAr).joinToString(" · "),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            if (tab == 1 && (rc.authorFounder != null || rc.brewedCount > 0)) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                rc.authorFounder?.let { FounderChip(it, compact = true, name = rc.authorName) }
                                if (rc.brewedCount > 0) Text("حضّرها ${rc.brewedCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Tone.olive)
                            }
                        }
                        rc.likeCount?.takeIf { it > 0 }?.let { Text("♥ $it", color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
    open?.let { rc ->
        ModalBottomSheet(onDismissRequest = { open = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            RecipeDetail(app, rc, onBrew = { open = null; val p = rc.toPreset(); nav.push(Route.Brewing(p, lotName ?: rc.lot?.nameAr, lotSlug ?: rc.lot?.slug)) },
                onEdit = { copy -> open = null; nav.push(Route.RecipeEditor(com.bunnlist.core.RecipeDraft.from(rc, copy).let { if (copy && it.lotSlug == null) it.copy(lotSlug = lotSlug) else it })) },
                onDeleted = { open = null; reload++ })
        }
    }
}

@Composable
private fun RecipeDetail(app: BunnListApp, rc: RemoteRecipe, onBrew: () -> Unit, onEdit: (copy: Boolean) -> Unit, onDeleted: () -> Unit) {
    var confirmDelete by remember { mutableStateOf(false) }
    var like by remember { mutableStateOf(com.bunnlist.core.LikeState(rc.likeCount ?: 0, rc.likedByMe ?: false)) }
    val scope = rememberCoroutineScope()
    val p = remember(rc) { rc.toPreset() }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(p.nameAr, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(p.sourceAr, color = MaterialTheme.colorScheme.secondary, fontSize = 13.sp)
        if (!rc.isMine && rc.authorName.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("بواسطة ${rc.authorName}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            rc.authorFounder?.let { FounderChip(it, compact = true, name = rc.authorName) }
        }
        if (!rc.isMine) SocialLinks(rc.authorSocials)
        if (rc.brewedCount > 0) Text("حضّرها ${rc.brewedCount} شخص", fontWeight = FontWeight.Bold, color = Tone.olive, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOfNotNull("${Methods.grams(p.doseGrams)}غ" to "بن", p.hotWaterGrams?.let { "${Methods.grams(it)}غ" to "ماء" }, p.iceGrams?.let { "${Methods.grams(it)}غ" to "ثلج" },
                p.yieldGrams?.let { "${Methods.grams(it)}غ" to "الناتج" }, p.waterTempC.takeIf { it > 0 }?.let { "${Methods.grams(it)}°" to "الحرارة" }).forEach { (v, k) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(v, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text(k, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        if (p.grindAr.isNotBlank()) Text("الطحنة: ${p.grindAr}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        p.steps.forEachIndexed { i, s ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp)) {
                Text("${i + 1}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(24.dp))
                Column {
                    Text(listOfNotNull(s.kind.nameAr, s.addedWaterGrams?.let { "+${Methods.grams(it)}غ" }, s.seconds?.let { Methods.clock(it) }).joinToString(" · "), fontWeight = FontWeight.Bold)
                    if (s.note.isNotBlank()) Text(s.note, fontSize = 13.sp)
                }
            }
        }
        if (rc.notes.isNotBlank()) Text(rc.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onBrew, enabled = p.steps.isNotEmpty(), modifier = Modifier.weight(1f).height(52.dp), shape = CircleShape) { Icon(Icons.Filled.PlayArrow, null); Text(" حضّر بهالوصفة", fontWeight = FontWeight.Bold) }
            if (!rc.isMine && app.session.isSignedIn) OutlinedButton(onClick = {
                val want = !like.liked; val before = like
                like = com.bunnlist.core.LikeState((like.count + if (want) 1 else -1).coerceAtLeast(0), want)
                scope.launch { runCatching { app.session.withToken { app.api.setLike("recipe", rc.id, want, it) } }.onSuccess { like = it }.onFailure { like = before } }
            }, modifier = Modifier.height(52.dp), shape = CircleShape) { Icon(if (like.liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, null, tint = MaterialTheme.colorScheme.secondary); Text(" ${like.count}") }
        }
        if (app.session.isSignedIn) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rc.isMine) {
                OutlinedButton(onClick = { onEdit(false) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Edit, null); Text(" عدّل") }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Icon(Icons.Filled.Delete, null); Text(" احذف") }
            } else OutlinedButton(onClick = { onEdit(true) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.ContentCopy, null); Text(" انسخها وعدّل عليها") }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("تحذف الوصفة؟") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { runCatching { app.session.withToken { app.api.deleteRecipe(rc.id, it) } }.onSuccess { onDeleted() } } },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("احذف") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } })
}

// MARK: Laurel

/** The iOS rating laurels: a branch of leaves on each side of the number. */
@Composable
fun Laurel(mirrored: Boolean, modifier: Modifier = Modifier) {
    val gold = Brand.Gold
    Canvas(modifier.size(34.dp, 56.dp)) {
        val w = size.width; val h = size.height
        val sign = if (mirrored) -1f else 1f
        val cx = if (mirrored) w * 0.2f else w * 0.8f
        // Stem: a gentle arc.
        val stem = androidx.compose.ui.graphics.Path().apply { moveTo(cx, h * 0.95f); quadraticTo(cx - sign * w * 0.75f, h * 0.5f, cx, h * 0.05f) }
        drawPath(stem, gold, style = Stroke(2.dp.toPx()))
        val pm = androidx.compose.ui.graphics.PathMeasure().apply { setPath(stem, false) }
        for (i in 1..6) {
            val d = pm.length * (i / 7f)
            val pos = pm.getPosition(d); val tan = pm.getTangent(d)
            val ang = Math.toDegrees(Math.atan2(tan.y.toDouble(), tan.x.toDouble())).toFloat()
            for (side in listOf(-1f, 1f)) {
                rotate(ang + side * 38f, pos) {
                    drawOval(gold, topLeft = Offset(pos.x, pos.y - 3.2.dp.toPx()), size = androidx.compose.ui.geometry.Size(11.dp.toPx(), 6.4.dp.toPx()))
                }
            }
        }
    }
}

// MARK: Passport

private val badgeIcons: Map<String, ImageVector> = mapOf(
    "seal.fill" to Icons.Filled.Verified, "map.fill" to Icons.Filled.Map, "airplane" to Icons.Filled.Flight, "globe.europe.africa.fill" to Icons.Filled.Public,
    "drop.triangle.fill" to Icons.Filled.WaterDrop, "mountain.2.fill" to Icons.Filled.Landscape, "sparkles" to Icons.Filled.AutoAwesome,
    "crown.fill" to Icons.Filled.EmojiEvents, "flask.fill" to Icons.Filled.Science, "square.stack.3d.up.fill" to Icons.Filled.Layers,
)

/** «جواز القهوة»: a stamp for every origin tasted, and badges for breadth. */
@Composable
fun PassportScreen(app: BunnListApp, nav: Nav) {
    var passport by remember { mutableStateOf<Passport?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) { failed = false; try { passport = app.session.withToken { app.api.passport(it) } } catch (e: ApiError) { failed = true } }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("جواز القهوة", onBack = { nav.pop() })
        val p = passport
        when {
            p == null && failed -> Retry("ما قدرنا نفتح الجواز") { reload++ }
            p == null -> Loading()
            else -> LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val full = androidx.compose.foundation.lazy.grid.GridItemSpan(3)
                item(span = { full }) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Brand.Olive).padding(5.dp).border(1.dp, Brand.Gold.copy(alpha = 0.6f), RoundedCornerShape(18.dp)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("جواز القهوة", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Brand.Cream)
                                Text("COFFEE PASSPORT", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp, color = Brand.Cream.copy(alpha = 0.7f))
                            }
                            Icon(Icons.Filled.Public, null, tint = Brand.Gold, modifier = Modifier.size(34.dp))
                        }
                        Row {
                            listOf(p.countries to "بلد", p.regions to "منطقة من ٤", p.processes to "معالجة", p.earnedCount to "وسام").forEach { (v, l) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$v", fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Brand.Cream); Text(l, fontSize = 11.sp, color = Brand.Cream.copy(alpha = 0.85f))
                                }
                            }
                        }
                    }
                }
                item(span = { full }) { Text("الأختام", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                if (p.stamps.isEmpty()) item(span = { full }) {
                    Text("جرّب أي قهوة أو قيّمها أو أضفها لمجموعتك وتنختم أول صفحة", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp))
                } else itemsIndexed(p.stamps, key = { _, s -> s.code }) { i, s -> Stamp(s, listOf(-8f, 5f, -3f, 9f, -6f, 3f)[i % 6]) }
                item(span = { full }) { Text("الأوسمة", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp)) }
                p.badges.sortedByDescending { it.earned }.forEach { b ->
                    item(span = { full }, key = "b" + b.id) {
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(46.dp).clip(CircleShape).background(if (b.earned) Brand.Gold else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                                Icon(badgeIcons[b.icon] ?: Icons.Filled.MilitaryTech, null, tint = if (b.earned) Brand.Cream else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            }
                            Column(Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(b.titleAr, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    if (b.earned) Icon(Icons.Filled.Verified, null, tint = MaterialTheme.colorScheme.primary) else Text("${b.current}/${b.goal}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(b.detailAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!b.earned) LinearProgressIndicator(progress = { b.progress }, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Stamp(s: Passport.Stamp, tilt: Float) {
    val color = when (s.region) { "africa" -> Brand.Brown; "americas" -> Brand.Olive; "arabia" -> Brand.Gold; else -> MaterialTheme.colorScheme.onBackground }
    val month = runCatching { java.time.Instant.parse(s.firstAt).atZone(java.time.ZoneId.systemDefault()).let { "${it.monthValue}/${it.year}" } }.getOrNull()
    Box(Modifier.size(96.dp).rotate(tilt), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color, style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
            drawCircle(color.copy(alpha = 0.5f), radius = size.minDimension / 2 - 5.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(com.bunnlist.core.RemoteLot(id = "", slug = "", name = "", nameAr = "", roasterNameAr = "", process = "", originCode = s.code).flag ?: "", fontSize = 22.sp)
            Text(s.nameAr, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            month?.let { Text(it, fontSize = 9.sp, color = color) }
            if (s.count > 1) Text("×${s.count}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
