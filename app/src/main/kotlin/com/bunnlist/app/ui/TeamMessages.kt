package com.bunnlist.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.HomeText
import com.bunnlist.core.TeamMessage
import com.bunnlist.core.TeamMessages

/** «رسائل BunnList»: messages from the team, newest first. One way; opening the screen marks them read. */
@androidx.compose.runtime.Composable
fun TeamMessagesScreen(app: BunnListApp, nav: Nav) {
    var data by remember { mutableStateOf<TeamMessages?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    LaunchedEffect(reload) {
        failed = false
        try {
            val d = app.session.withToken { app.api.teamMessages(it) }
            data = d
            if (d.unread > 0) runCatching { app.session.withToken { app.api.markTeamMessagesRead(it) } }
        } catch (e: ApiError) { failed = data == null }
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
        TopBar("رسائل BunnList", onBack = { nav.pop() })
        val d = data
        when {
            d == null && failed -> Retry("تعذّر التحميل") { reload++ }
            d == null -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(120.dp), 20.dp) } }
            d.messages.isEmpty() -> Empty("ما فيه رسائل", "أي رسالة من فريق BunnList لك بتطلع هنا.")
            else -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(d.messages, key = { it.id }) { TeamMessageCard(it) }
                item {
                    Text("هذي رسائل من طرف واحد. تبي تراسلنا؟ من «صندوق اقتراحاتك» في الإعدادات.", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
        }
    }
}

private fun kindStyle(kind: String): Pair<ImageVector, Color> = when (kind) {
    "warning" -> Icons.Filled.Warning to Color(0xFFC2593A)
    "suspension" -> Icons.Filled.PanTool to Color(0xFFC2593A)
    "pointsReset" -> Icons.Filled.Refresh to Brand.Gold
    "good" -> Icons.Filled.AutoAwesome to Brand.Olive
    else -> Icons.Filled.Email to Brand.Brown
}

@androidx.compose.runtime.Composable
private fun TeamMessageCard(m: TeamMessage) {
    val (icon, tint) = kindStyle(m.kind)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(20.dp), tint = Color.White) }
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text(m.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(HomeText.ago(m.createdAt), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!m.read) Box(Modifier.size(9.dp).clip(CircleShape).background(tint))
        }
        Text(m.body, fontSize = 15.sp, lineHeight = 22.sp)
        Text("— فريق BunnList", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Brand.Olive)
    }
}

/** At the top of «لك» while there's an unread message from the team. */
@androidx.compose.runtime.Composable
fun TeamMessagesBanner(unread: Int, onOpen: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brand.Olive.copy(alpha = 0.12f)).pressable(onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Brand.Olive), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.MarkEmailUnread, null, tint = Color.White)
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(if (unread == 1) "عندك رسالة من فريق BunnList" else "عندك $unread رسائل من فريق BunnList", fontWeight = FontWeight.Bold)
            Text("اضغط تقراها", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
