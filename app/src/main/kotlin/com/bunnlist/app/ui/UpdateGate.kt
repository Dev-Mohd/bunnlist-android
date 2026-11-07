package com.bunnlist.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bunnlist.app.BuildConfig
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.AppConfig

/**
 * Checks /app/config on launch and each time the app comes back: below the minimum versionCode, a
 * full-screen stop with a link to the store (back can't get past it); below the latest, a one-time nudge.
 */
@Composable
fun UpdateGate(app: BunnListApp, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf<AppConfig?>(null) }
    var nudge by remember { mutableStateOf(false) }
    var checks by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { checks++ }
    LaunchedEffect(checks) {
        val fresh = runCatching { app.api.appConfig() }.getOrNull() ?: return@LaunchedEffect
        config = fresh
        val prefs = context.getSharedPreferences("ui", 0)
        if (AppConfig.verdict(BuildConfig.VERSION_CODE, fresh.android) == AppConfig.Verdict.Suggested &&
            prefs.getInt("nudgedBuild", 0) < fresh.android.latestBuild) {
            prefs.edit().putInt("nudgedBuild", fresh.android.latestBuild).apply()
            nudge = true
        }
    }
    fun openStore() {
        val link = config?.android?.storeUrl?.takeIf { it.isNotBlank() } ?: "https://play.google.com/store/apps/details?id=${context.packageName}"
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    val c = config
    if (c != null && AppConfig.verdict(BuildConfig.VERSION_CODE, c.android) == AppConfig.Verdict.Required) {
        androidx.activity.compose.BackHandler(enabled = true) {}
        ForcedUpdate(c.updateMessageAr.ifBlank { "نزّلنا نسخة جديدة فيها تحسينات مهمة. حدّث التطبيق عشان تكمل." }) { openStore() }
        return
    }
    content()
    if (nudge) AlertDialog(onDismissRequest = { nudge = false },
        title = { Text("فيه تحديث جديد ✨") },
        text = { Text("نزّلنا نسخة جديدة من BunnList فيها تحسينات. حدّثها من المتجر.") },
        confirmButton = { TextButton(onClick = { nudge = false; openStore() }) { Text("حدّث الحين") } },
        dismissButton = { TextButton(onClick = { nudge = false }) { Text("بعدين") } })
}

@Composable
private fun ForcedUpdate(message: String, onUpdate: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        BrandMark(120.dp, animated = false)
        Spacer(Modifier.height(22.dp))
        Text("حدّث BunnList", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onBackground).pressable(onClick = onUpdate),
            contentAlignment = Alignment.Center) {
            Text("حدّث التطبيق", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.background)
        }
    }
}
