package com.bunnlist.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.CollectionFlags
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.Me
import com.bunnlist.core.Methods
import com.bunnlist.core.RemoteLot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First run after signing in (as iOS OnboardingFlow): set up the profile, say what you like, then pick the
 * coffees at home. Finishing or skipping records it on the server, so it shows once per account.
 */
@Composable
fun OnboardingFlow(app: BunnListApp, me: Me, onFinished: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun finish() { scope.launch { runCatching { app.session.withToken { app.api.markOnboarded(it) } }; onFinished() } }
    BackHandler(step > 0) { step-- }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { i ->
                val w by animateDpAsState(if (i == step) 24.dp else 8.dp, label = "step$i")
                Box(Modifier.padding(horizontal = 3.dp).size(w, 8.dp).clip(CircleShape).background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)))
            }
        }
        AnimatedContent(step, label = "onboarding", transitionSpec = { (slideInHorizontally { -it } + fadeIn()) togetherWith (slideOutHorizontally { it / 3 } + fadeOut()) }) { s ->
            when (s) {
                0 -> ProfileSetupStep(app, me) { step = 1 }
                1 -> TastePrefsStep(app, me) { step = 2 }
                else -> FirstCoffeesStep(app) { finish() }
            }
        }
    }
}

@Composable
private fun ProfileSetupStep(app: BunnListApp, me: Me, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var avatar by remember { mutableStateOf(me.avatarUrl) }
    var name by remember { mutableStateOf(me.displayName ?: "") }
    var username by remember { mutableStateOf(me.username ?: "") }
    var bio by remember { mutableStateOf(me.bio ?: "") }
    var check by remember { mutableStateOf<com.bunnlist.core.UsernameCheck?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { if (username.isBlank()) app.session.withToken { app.api.suggestUsername(it) }?.let { username = it } }
    LaunchedEffect(username) {
        check = null; val u = username.trim().lowercase()
        if (u.isEmpty() || u == me.username) return@LaunchedEffect
        delay(400); check = runCatching { app.session.withToken { app.api.checkUsername(u, it) } }.getOrNull()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch { runCatching { val b = withContext(Dispatchers.Default) { encode(context, uri, 512) }!!; app.session.withToken { app.api.setAvatar(b, it) } }.onSuccess { avatar = it.avatarUrl } }
    }
    val ok = name.isNotBlank() && username.trim().lowercase().let { it == me.username || (check?.available == true && check?.username == it) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("خلّنا نجهّز ملفك", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("هذا اللي يشوفه الناس على وصفاتك وتقييماتك والقهوة اللي تكتشفها.", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Box(Modifier.pressable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Avatar(avatar, name.ifBlank { username }, 96.dp)
            Box(Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PhotoCamera, "اختر صورة الملف", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        OutlinedTextField(name, { name = it.take(40) }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            OutlinedTextField(username, { username = it.take(20).lowercase().filter { c -> c.isLetterOrDigit() || c == '_' || c == '.' } }, label = { Text("username") },
                prefix = { Text("@") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = check?.available == false,
                supportingText = { val c = check; Text(when { username.trim().lowercase() == me.username -> "اسمك الحالي"; c == null -> "حروف إنجليزية صغيرة وأرقام و _ و ."; c.available -> "متاح ✓"; else -> c.problemAr ?: "" }) })
        }
        OutlinedTextField(bio, { bio = it.take(160) }, label = { Text("نبذة (اختياري)") }, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${bio.length}/160") })
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(enabled = ok && !busy, onClick = {
            busy = true; error = null
            scope.launch {
                try {
                    app.session.withToken { t -> val u = username.trim().lowercase(); if (u != me.username) app.api.setUsername(u, t); app.api.updateProfile(t, displayName = name, bio = bio) }
                    onDone()
                } catch (e: ApiError) { error = if (e is ApiError.Conflict) "الاسم مستخدم، جرّب غيره" else "ما انحفظ، جرّب مرة ثانية." }
                busy = false
            }
        }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text(if (busy) "نحفظ…" else "التالي", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDone) { Text("لاحقًا", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TastePrefsStep(app: BunnListApp, me: Me, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var taste by remember { mutableStateOf(me.tastePrefs.toSet()) }
    var brew by remember { mutableStateOf(me.brewPrefs.toSet()) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("وش تحب في قهوتك؟", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("اختر النكهات اللي تشدّك، ونرتّب لك القهاوي على ذوقك.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FlavorFamily.advisorChoices.forEach { f ->
                val on = f.code in taste
                Row(Modifier.clip(CircleShape).background(if (on) f.color() else MaterialTheme.colorScheme.surface).pressable { taste = if (on) taste - f.code else taste + f.code }
                    .padding(horizontal = 16.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(if (on) Color.White else f.color()))
                    Text("  ${f.shortNameAr}", fontWeight = FontWeight.Bold, color = if (on) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text("وش تستخدم؟", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("V60", "ESPRESSO", "CHEMEX", "AEROPRESS", "FRENCH_PRESS", "COLD_BREW").forEach { m ->
                FilterChip(selected = m in brew, onClick = { brew = if (m in brew) brew - m else brew + m }, label = { Text(Methods.nameAr(m)) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { scope.launch { runCatching { app.session.withToken { app.api.setPrefs(it, taste.toList(), brew.toList()) } }; onDone() } },
            modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text("التالي", fontWeight = FontWeight.Bold) }
        TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("تخطّي", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun FirstCoffeesStep(app: BunnListApp, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var lots by remember { mutableStateOf<List<RemoteLot>?>(null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Map<String, RemoteLot>>(emptyMap()) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(query) {
        delay(if (query.isBlank()) 0 else 350)
        lots = runCatching { app.session.withToken { app.api.browse(query.takeIf { it.trim().length >= 2 }, limit = 40, accessToken = it).lots } }.getOrDefault(emptyList())
    }
    Column(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            item(span = { GridItemSpan(3) }) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("وش عندك في البيت؟", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("اختر لين ٣ قهوات عندك الحين وتنضاف لمجموعتك. تقدر تمسح أكياسك بعدين وتضيف أكثر.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(query, { query = it }, placeholder = { Text("ابحث عن قهوة أو محمصة") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp))
                }
            }
            val l = lots
            if (l == null) items(9) { SkeletonBlock(Modifier.fillMaxWidth().aspectRatio(0.8f), 14.dp) }
            else items(l, key = { it.id }) { lot ->
                val on = lot.id in selected
                val full = !on && selected.size >= 3
                Column(Modifier.pressable(enabled = !full) { selected = if (on) selected - lot.id else selected + (lot.id to lot) }.alpha(if (full) 0.4f else 1f)) {
                    Box {
                        LotPhotoWell(lot.imageUrl, lot.flavorNotes, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp))
                            .then(if (on) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp)) else Modifier), 4.dp)
                        if (on) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(6.dp).size(22.dp).background(Color.White, CircleShape))
                    }
                    Text(lot.nameAr, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text(lot.roasterNameAr, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            Text("اخترت ${selected.size} من ٣", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(enabled = selected.isNotEmpty() && !saving, onClick = {
                saving = true; error = null
                scope.launch {
                    try { app.session.withToken { t -> selected.values.forEach { app.api.setCollection(it.slug, CollectionFlags(owned = true), t) } }; onDone() }
                    catch (e: ApiError) { error = "ما انضافت، جرّب مرة ثانية." }
                    saving = false
                }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) { Text(if (saving) "نضيفها…" else "أضفها لمجموعتي", fontWeight = FontWeight.Bold) }
            TextButton(onClick = onDone) { Text("تخطّي", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

