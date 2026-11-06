package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.RemoteLot
import kotlinx.coroutines.delay

/** «اكتشف»: the full catalog from the same API as iOS, with search and endless scrolling. */
@Composable
fun DiscoverScreen(app: BunnListApp) {
    var query by remember { mutableStateOf("") }
    var lots by remember { mutableStateOf<List<RemoteLot>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    suspend fun load(reset: Boolean) {
        if (loading) return
        loading = true; failed = false
        try {
            val token = if (app.session.isSignedIn) runCatching { app.session.accessToken() }.getOrNull() else null
            val page = app.api.browse(query, limit = 30, offset = if (reset) 0 else lots.size, accessToken = token)
            total = page.total
            lots = if (reset) page.lots else lots + page.lots
        } catch (e: ApiError) { failed = true } finally { loading = false }
    }

    LaunchedEffect(query) { delay(if (query.isEmpty()) 0 else 350); load(reset = true) }
    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= lots.size - 5 } }
    LaunchedEffect(nearEnd, lots.size) { if (nearEnd && lots.isNotEmpty() && lots.size < total) load(reset = false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding()) {
        Text("قهوتك الجاية، هنا.", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        OutlinedTextField(query, { query = it }, placeholder = { Text("ابحث عن قهوة أو محمصة") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        if (total > 0) Text("${total} قهوة", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        when {
            failed && lots.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("ما قدرنا نجيب القهوات"); TextButton(onClick = { failed = false; query = query.trim() + "" }) { Text("إعادة المحاولة") }
                }
            }
            lots.isEmpty() && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            else -> LazyColumn(state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(lots, key = { it.id }) { LotRow(it) }
                if (loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
        }
    }
}

@Composable
private fun LotRow(lot: RemoteLot) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(model = lot.imageUrl, contentDescription = lot.nameAr, contentScale = ContentScale.Fit,
                modifier = Modifier.size(92.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lot.nameAr, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lot.roasterNameAr, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text(listOfNotNull(lot.flag, lot.originNameAr, lot.roastLevel).joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                if (lot.hasRatings) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Text(" ${"%.1f".format(lot.averageRating)} (${lot.reviewCount})", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                } else if (lot.flavorNotes.isNotEmpty()) {
                    Text(lot.flavorNotes.take(3).joinToString(" · "), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
