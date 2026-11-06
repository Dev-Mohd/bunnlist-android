package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.app.R
import com.bunnlist.core.ApiError
import com.bunnlist.core.Comment
import com.bunnlist.core.RelativeTime
import com.bunnlist.core.ReportReason
import kotlinx.coroutines.launch

@Composable
fun Avatar(url: String?, name: String?, size: Dp = 40.dp, official: Boolean = false) {
    Box(Modifier.size(size).clip(CircleShape).background(if (official) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center) {
        when {
            official -> androidx.compose.foundation.Image(painterResource(R.mipmap.ic_launcher_foreground), null, Modifier.fillMaxSize())
            url != null -> AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else -> Text((name?.trim()?.takeIf { it.isNotEmpty() } ?: "☕").take(1), fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = (size.value * 0.42f).sp)
        }
    }
}

@Composable
fun OfficialName(size: Int = 15) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("BunnList", fontWeight = FontWeight.Bold, fontSize = size.sp)
        Icon(Icons.Filled.Verified, "الحساب الرسمي", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(start = 3.dp).size((size - 2).dp))
    }
}

/** Pick a reason and send a report. */
@Composable
fun ReportDialog(title: String, onDismiss: () -> Unit, onSend: suspend (ReportReason, String) -> Boolean) {
    var reason by remember { mutableStateOf(ReportReason.Offensive) }
    var details by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                ReportReason.entries.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { reason = r }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = reason == r, onClick = { reason = r }); Text(r.nameAr)
                    }
                }
                OutlinedTextField(details, { details = it.take(500) }, placeholder = { Text("تفاصيل (اختياري)") }, modifier = Modifier.fillMaxWidth())
                if (failed) Text("ما انرسل البلاغ، جرّب مرة ثانية.", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = { busy = true; scope.launch { if (onSend(reason, details)) onDismiss() else failed = true; busy = false } }) { Text("أرسل البلاغ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

/** Comments under a post: read, write, delete your own, report or block others. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheet(app: BunnListApp, type: String, id: String, title: String, onDismiss: () -> Unit, onCount: (Int) -> Unit = {}) {
    var comments by remember { mutableStateOf<List<Comment>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    var reporting by remember { mutableStateOf<Comment?>(null) }
    var blocking by remember { mutableStateOf<Comment?>(null) }
    val scope = rememberCoroutineScope()
    val signedIn = app.session.isSignedIn
    suspend fun load() {
        try {
            val token = if (signedIn) runCatching { app.session.accessToken() }.getOrNull() else null
            comments = app.api.comments(type, id, token).comments; failed = false
        } catch (e: ApiError) { failed = true }
    }
    LaunchedEffect(Unit) { load() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding()) {
            Text("التعليقات", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(horizontal = 20.dp))
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 20.dp))
            val list = comments
            Box(Modifier.weight(1f)) {
                when {
                    list == null && failed -> Text("ما قدرنا نجيب التعليقات", Modifier.align(Alignment.Center))
                    list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    list.isEmpty() -> Text("كن أول من يعلّق", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(list, key = { it.id }) { c ->
                            Row(verticalAlignment = Alignment.Top) {
                                Avatar(c.avatarUrl, c.name, 36.dp, official = c.isOfficial)
                                Column(Modifier.padding(start = 10.dp).weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (c.isOfficial) OfficialName(14) else Text(c.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("  ${RelativeTime.ar(c.createdAt)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text(c.body)
                                }
                                if (signedIn && (c.canDelete || (!c.isMine && !c.isOfficial))) {
                                    var menu by remember { mutableStateOf(false) }
                                    Box {
                                        IconButton(onClick = { menu = true }, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.MoreVert, "خيارات", Modifier.size(18.dp)) }
                                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                            if (c.canDelete) DropdownMenuItem(text = { Text("احذف") }, onClick = {
                                                menu = false
                                                scope.launch {
                                                    runCatching { app.session.withToken { app.api.deleteComment(c.id, it) } }
                                                        .onSuccess { comments = comments?.filterNot { it.id == c.id }; onCount(comments?.size ?: 0) }
                                                }
                                            })
                                            if (!c.isMine && !c.isOfficial) {
                                                DropdownMenuItem(text = { Text("بلّغ") }, onClick = { menu = false; reporting = c })
                                                if (c.username != null) DropdownMenuItem(text = { Text("احظر @${c.username}") }, onClick = { menu = false; blocking = c })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            info?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
            if (signedIn) Row(Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(text, { text = it.take(500) }, placeholder = { Text("اكتب تعليق…") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), maxLines = 4)
                IconButton(enabled = text.isNotBlank() && !sending, onClick = {
                    sending = true; info = null
                    scope.launch {
                        try {
                            val c = app.session.withToken { app.api.addComment(type, id, text, it) }
                            comments = (comments ?: emptyList()) + c; text = ""; onCount(comments!!.size)
                        } catch (e: ApiError) {
                            info = when (e) { ApiError.RateLimited -> "علّقت كثير، استنى شوي."; ApiError.Forbidden -> "حسابك موقوف عن النشر حاليًا."; else -> "ما انرسل، جرّب مرة ثانية." }
                        }
                        sending = false
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, "أرسل", tint = MaterialTheme.colorScheme.primary) }
            } else Text("سجّل دخولك عشان تعلّق.", Modifier.padding(20.dp).navigationBarsPadding(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    reporting?.let { c ->
        ReportDialog("بلّغ عن التعليق", onDismiss = { reporting = null }) { r, d ->
            runCatching { app.session.withToken { app.api.report("comment", c.id, r, d, it) } }.isSuccess.also { if (it) info = "وصلنا بلاغك، شكرًا لك." }
        }
    }
    blocking?.let { c ->
        AlertDialog(onDismissRequest = { blocking = null }, title = { Text("تحظر @${c.username}؟") },
            text = { Text("ما راح تشوف تعليقاته ونشاطه، وهو ما يقدر يتابعك أو يعلّق على منشوراتك.") },
            confirmButton = {
                TextButton(onClick = {
                    val u = c.username!!; blocking = null
                    scope.launch {
                        runCatching { app.session.withToken { app.api.setBlocked(u, true, it) } }
                            .onSuccess { comments = comments?.filterNot { it.username == u }; info = "حظرت @$u." }
                            .onFailure { info = "ما انحظر، جرّب مرة ثانية." }
                    }
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("احظر") }
            },
            dismissButton = { TextButton(onClick = { blocking = null }) { Text("إلغاء") } })
    }
}

/** Behind coffee bag photos (they are shot on white). */
val CardWhite = Color.White
