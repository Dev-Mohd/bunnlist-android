package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.CollectionFlags
import com.bunnlist.core.FlavorFamily
import com.bunnlist.core.Methods
import com.bunnlist.core.RemoteLot
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * «أضف قهوة» (approved mockup, as iOS AddCoffeeSearchView): search as you type; each result shows the bag, the
 * roaster with the flag, process and rating, and its flavour notes in their colours, with the typed letters
 * shaded. Coffees you have come first («في مجموعتك», «✓ عندك»), then «من الدليل». Quick filters by country,
 * process or «عندي». Under the results: «ما لقيت قهوتك؟ اقترحها» with what you typed.
 */
@Composable
fun AddCoffeeScreen(app: BunnListApp, nav: Nav) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<RemoteLot>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var owned by remember { mutableStateOf<Set<String>>(emptySet()) }
    var added by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filter by remember { mutableStateOf<String?>(null) }   // "o:ET", "p:NATURAL", "mine"
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val q = query.trim()
    LaunchedEffect(Unit) {
        if (app.session.isSignedIn) owned = runCatching { app.session.withToken { app.api.collection(it) } }.getOrDefault(emptyList()).filter { it.owned }.map { it.slug }.toSet()
    }
    LaunchedEffect(q) {
        if (q.length < 2) { results = null; return@LaunchedEffect }
        delay(300); loading = true
        results = runCatching { app.api.browse(q, limit = 40, offset = 0, accessToken = null) }.getOrNull()?.lots ?: emptyList()
        filter = null; loading = false
    }
    fun have(l: RemoteLot) = l.slug in owned || l.slug in added
    val all = results.orEmpty()
    val shown = all.filter { l -> when {
        filter == null -> true; filter == "mine" -> have(l)
        filter!!.startsWith("o:") -> l.originCode == filter!!.drop(2); else -> l.process == filter!!.drop(2)
    } }
    fun top(codes: List<String>) = codes.groupingBy { it }.eachCount().filter { it.value >= 2 }.entries.sortedByDescending { it.value }.take(2).map { it.key }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        TopBar("أضف قهوة", onBack = { nav.pop() })
        OutlinedTextField(query, { query = it }, placeholder = { Text("ابحث بالاسم أو المحمصة") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, "امسح") } },
            shape = RoundedCornerShape(18.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = MaterialTheme.colorScheme.surface, focusedContainerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        message?.let { Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 8.dp)) }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (all.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    QuickChip("الكل ${all.size}", filter == null) { filter = null }
                    top(all.mapNotNull { it.originCode }).forEach { c ->
                        val l = all.first { it.originCode == c }
                        QuickChip(listOfNotNull(l.flag, l.originNameAr).joinToString(" "), filter == "o:$c") { filter = if (filter == "o:$c") null else "o:$c" }
                    }
                    top(all.map { it.process }).forEach { c -> QuickChip(Methods.processAr(c) ?: c, filter == "p:$c") { filter = if (filter == "p:$c") null else "p:$c" } }
                    if (all.any { have(it) }) QuickChip("عندي", filter == "mine") { filter = if (filter == "mine") null else "mine" }
                }
            }
            if (loading && results == null) items(4) { SkeletonBlock(Modifier.fillMaxWidth().height(80.dp), 18.dp) }
            val mine = shown.filter { have(it) }; val others = shown.filter { !have(it) }
            if (mine.isNotEmpty()) { item { GroupLabel("في مجموعتك") }; items(mine, key = { "m" + it.id }) { l -> ResultRow(l, q, true, onOpen = { nav.push(Route.Lot(l.slug)) }) {} } }
            if (others.isNotEmpty()) { item { GroupLabel("من الدليل") }; items(others, key = { "o" + it.id }) { l ->
                ResultRow(l, q, false, onOpen = { nav.push(Route.Lot(l.slug)) }) {
                    if (!app.session.isSignedIn) { message = "سجّل الدخول من «حسابي» لإضافة القهوة إلى مجموعتك."; return@ResultRow }
                    scope.launch {
                        runCatching { app.session.withToken { app.api.setCollection(l.slug, CollectionFlags(owned = true), it) } }
                            .onSuccess { added = added + l.slug }.onFailure { message = "تعذّرت الإضافة. حاول مجددًا." }
                    }
                }
            } }
            if (results != null && all.isEmpty()) item { Text("ما لقينا «$q» في الدليل.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (q.isNotEmpty()) item { SuggestFromSearchCard(q) { nav.push(Route.SuggestCoffee(q, "", null)) } }
        }
    }
}

@Composable
private fun GroupLabel(text: String) = Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp))

@Composable
private fun QuickChip(title: String, on: Boolean, onClick: () -> Unit) {
    Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.clip(CircleShape).background(if (on) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surface).pressable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp))
}

@Composable
private fun ResultRow(l: RemoteLot, q: String, have: Boolean, onOpen: () -> Unit, onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface).pressable(onClick = onOpen).padding(9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        LotPhotoWell(l.imageUrl, l.flavorNotes, Modifier.size(52.dp, 62.dp).clip(RoundedCornerShape(12.dp)), 3.dp, roaster = l.roasterNameAr, name = l.nameAr,
            origin = l.originNameAr, originCode = l.originCode)
        Column(Modifier.weight(1f).padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            val shade = Brand.Gold.copy(alpha = 0.25f)
            Text(buildAnnotatedString {
                val i = if (q.length >= 2) l.nameAr.indexOf(q, ignoreCase = true) else -1
                if (i < 0) append(l.nameAr) else {
                    append(l.nameAr.substring(0, i)); withStyle(SpanStyle(background = shade)) { append(l.nameAr.substring(i, i + q.length)) }; append(l.nameAr.substring(i + q.length))
                }
            }, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val place = listOfNotNull(l.flag, Methods.processAr(l.process)).joinToString(" ")
            Text(listOfNotNull(l.roasterNameAr, place.ifBlank { null }, if (l.hasRatings) "★ ${String.format(java.util.Locale.ROOT, "%.1f", l.averageRating)}" else null).joinToString(" · "),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (l.flavorNotes.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                l.flavorNotes.take(2).forEach { n ->
                    val c = FlavorFamily.of(n).let { if (it == FlavorFamily.Other) Brand.Gold else it.color() }
                    Text(n, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = c, modifier = Modifier.clip(CircleShape).background(c.copy(alpha = 0.15f)).padding(horizontal = 7.dp, vertical = 2.dp))
                }
            }
        }
        if (have) Text("✓ عندك", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.Olive,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.background).padding(horizontal = 10.dp, vertical = 6.dp))
        else Text("أضف", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White,
            modifier = Modifier.clip(CircleShape).background(Brand.Olive).pressable(onClick = onAdd).padding(horizontal = 14.dp, vertical = 6.dp))
    }
}

/** «ما لقيت قهوتك؟ اقترحها»: opens the suggestion form with the typed name. */
@Composable
internal fun SuggestFromSearchCard(q: String, onClick: () -> Unit) {
    val line = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .drawBehind { drawRoundRect(line, cornerRadius = androidx.compose.ui.geometry.CornerRadius(18.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 12f)))) }
        .pressable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(Brand.Gold.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Text("✦", color = Brand.Gold, fontSize = 18.sp) }
        Column(Modifier.padding(start = 12.dp)) {
            Text("ما لقيت قهوتك؟ اقترحها", fontWeight = FontWeight.Bold)
            Text(if (q.isBlank()) "صوّر الكيس ونعبّي الباقي" else "بالاسم «$q»، أو صوّر الكيس", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
