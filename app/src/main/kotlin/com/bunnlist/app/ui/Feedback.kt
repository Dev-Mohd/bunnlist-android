package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.Feedback
import com.bunnlist.core.FeedbackKind
import kotlinx.coroutines.launch

/** «تواصل معنا»: send the team a suggestion, an experience or a problem, and read their replies. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(app: BunnListApp, nav: Nav) {
    var kind by remember { mutableStateOf(FeedbackKind.Suggestion) }
    var text by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var mine by remember { mutableStateOf<List<Feedback>>(emptyList()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { mine = runCatching { app.session.withToken { app.api.myFeedback(it) } }.getOrDefault(emptyList()) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding().imePadding()) {
        TopBar("تواصل معنا", onBack = { nav.pop() })
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("رسالتك لفريق BunnList", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    FeedbackKind.entries.forEachIndexed { i, k ->
                        SegmentedButton(selected = kind == k, onClick = { kind = k }, shape = SegmentedButtonDefaults.itemShape(i, FeedbackKind.entries.size)) { Text(k.nameAr) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(text, { text = it.take(2000) }, placeholder = { Text(kind.placeholder) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 130.dp), shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.height(10.dp))
                Button(enabled = !sending && text.trim().length >= 3, onClick = {
                    sending = true; message = null
                    scope.launch {
                        try {
                            val sent = app.session.withToken { app.api.sendFeedback(kind, text, it) }
                            mine = listOf(sent) + mine; text = ""; message = "وصلتنا، شكرًا لك!"
                        } catch (e: ApiError) {
                            message = if (e == ApiError.RateLimited) "أرسلت رسائل كثير اليوم، كمّل بكرة." else "ما انرسلت، جرّب مرة ثانية."
                        }
                        sending = false
                    }
                }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(50)) { Text(if (sending) "نرسل…" else "أرسل") }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                Text("نرد عليك هنا.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                if (mine.isNotEmpty()) Text("رسائلك", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 16.dp))
            }
            items(mine, key = { it.id }) { f ->
                Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(FeedbackKind.of(f.kind).nameAr, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(f.body)
                        val reply = f.reply
                        if (reply != null) Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)).padding(10.dp)) {
                            Text("BunnList ✓", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                            Text(reply)
                        } else Text("وصلتنا، بنرد عليك قريب", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
