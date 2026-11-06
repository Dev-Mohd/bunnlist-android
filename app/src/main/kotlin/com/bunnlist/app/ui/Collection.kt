package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.CollectionFlags
import com.bunnlist.core.CollectionItem
import com.bunnlist.core.Shelf

/** «مجموعتي»: everything you saved, by shelf. Synced with the iOS app through the same account. */
@Composable
fun CollectionScreen(app: BunnListApp, nav: Nav, signedIn: Boolean, onSignIn: () -> Unit) {
    if (!signedIn) { SignInPrompt("مجموعتك تنحفظ في حسابك", "سجّل دخولك عشان تحفظ القهوات اللي عندك واللي تبي تجربها.", onSignIn); return }
    var items by remember { mutableStateOf<List<CollectionItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var shelf by remember { mutableStateOf(Shelf.All) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        failed = false
        try { items = app.session.withToken { app.api.collection(it) } } catch (e: ApiError) { failed = true }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Text("مجموعتي", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        val list = items
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Shelf.entries.forEach { s ->
                val n = list?.count(s.matches)
                FilterChip(selected = shelf == s, onClick = { shelf = s }, label = { Text(if (n != null) "${s.nameAr} ($n)" else s.nameAr) })
            }
        }
        when {
            list == null && failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ما قدرنا نجيب مجموعتك"); TextButton(onClick = { attempt++ }) { Text("إعادة المحاولة") }
                }
            }
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> {
                val shown = list.filter(shelf.matches)
                if (shown.isEmpty()) Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("☕", fontSize = 48.sp)
                        Text(if (list.isEmpty()) "مجموعتك فاضية" else "ما فيه شي هنا", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("افتح أي قهوة واضغط «عندي» أو «أبي أجربها».", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else LazyVerticalGrid(GridCells.Adaptive(160.dp), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp + LocalBottomInset.current),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(shown, key = { it.lotId }) { item -> ShelfCard(item) { nav.push(Route.Lot(item.slug)) } }
                }
            }
        }
    }
}

@Composable
private fun ShelfCard(item: CollectionItem, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(10.dp)) {
            Box {
                AsyncImage(item.imageUrl, item.nameAr, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(14.dp)).background(Color.White))
                if (item.favorite) Icon(Icons.Filled.Favorite, "مفضلة", tint = Color(0xFFC0392B), modifier = Modifier.padding(6.dp).size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(item.nameAr, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.roasterNameAr, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.flag, item.originNameAr).joinToString(" "), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SignInPrompt(title: String, body: String, onSignIn: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("☕", fontSize = 52.sp)
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = onSignIn, shape = RoundedCornerShape(50)) { Text("سجّل دخولك") }
        }
    }
}

/** The four collection toggles on a coffee page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollectionToggles(flags: CollectionFlags, busy: Boolean, onChange: (CollectionFlags) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple("عندي", flags.owned) { f: CollectionFlags -> f.copy(owned = !f.owned) },
            Triple("❤️ مفضلة", flags.favorite) { f: CollectionFlags -> f.copy(favorite = !f.favorite) },
            Triple("أبي أجربها", flags.wantsToTry) { f: CollectionFlags -> f.copy(wantsToTry = !f.wantsToTry, tried = if (!f.wantsToTry) false else f.tried) },
            Triple("جرّبتها", flags.tried) { f: CollectionFlags -> f.copy(tried = !f.tried, wantsToTry = if (!f.tried) false else f.wantsToTry) },
        ).forEach { (label, on, toggle) ->
            FilterChip(selected = on, enabled = !busy, onClick = { onChange(toggle(flags)) }, label = { Text(label, fontWeight = FontWeight.SemiBold) })
        }
    }
}
