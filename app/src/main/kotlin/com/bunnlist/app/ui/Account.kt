package com.bunnlist.app.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.bunnlist.app.BunnListApp
import com.bunnlist.app.BuildConfig
import com.bunnlist.core.ApiError
import com.bunnlist.core.Me
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(app: BunnListApp, nav: Nav, signedIn: Boolean, onSignIn: () -> Unit, onSignedOut: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<Me?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmSignOut by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(signedIn) { if (signedIn) me = runCatching { app.session.withToken { app.api.me(it) } }.getOrNull() }
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("حسابي", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
        Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    val m = me
                    if (m?.avatarUrl != null) AsyncImage(m.avatarUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    else Text((m?.displayName ?: "☕").take(1), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.padding(start = 14.dp).weight(1f)) {
                    if (signedIn) {
                        Text(me?.displayName ?: me?.username ?: "…", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                        me?.username?.let { Text("@$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        me?.bio?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp) }
                    } else {
                        Text("تتصفّح كضيف", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        TextButton(onClick = onSignIn, contentPadding = PaddingValues(0.dp)) { Text("سجّل دخولك أو أنشئ حساب") }
                    }
                }
            }
        }
        Group {
            if (signedIn) Item(Icons.Filled.Email, "تواصل معنا", "اقتراح، تجربة، أو مشكلة") { nav.push(Route.Feedback) }
            Item(Icons.Filled.Lock, "سياسة الخصوصية", null) { open("https://bunnlist.com/privacy") }
            Item(Icons.Filled.Description, "الشروط", null) { open("https://bunnlist.com/terms") }
        }
        if (signedIn) Group {
            Item(Icons.AutoMirrored.Filled.Logout, "تسجيل الخروج", null) { confirmSignOut = true }
            Item(Icons.Filled.DeleteForever, if (deleting) "نحذف…" else "حذف الحساب", "يحذف حسابك وكل بياناتك نهائيًا", danger = true) { confirmDelete = true }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text("BunnList ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(LocalBottomInset.current))
    }

    if (confirmSignOut) AlertDialog(onDismissRequest = { confirmSignOut = false },
        title = { Text("تسجيل الخروج؟") },
        confirmButton = { TextButton(onClick = { confirmSignOut = false; app.session.signOut(); onSignedOut() }) { Text("اخرج") } },
        dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("إلغاء") } })

    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false },
        title = { Text("تحذف حسابك نهائيًا؟") },
        text = { Text("بينحذف حسابك ومجموعتك وتقييماتك ووصفاتك وتعليقاتك، وما نقدر نرجعها. نفس الحساب في الآيفون بينحذف بعد.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false; deleting = true; error = null
                scope.launch {
                    try { app.session.withToken { app.api.deleteAccount(it) }; app.session.signOut(); onSignedOut() }
                    catch (e: ApiError) { error = "ما قدرنا نحذف الحساب الحين، جرّب مرة ثانية." }
                    deleting = false
                }
            }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("احذف نهائيًا") }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } })
}

@Composable
private fun Group(content: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), content = content)
    }
}

@Composable
private fun Item(icon: ImageVector, title: String, subtitle: String?, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.primary)
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(title, color = color, fontWeight = FontWeight.SemiBold)
                subtitle?.let { Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ComingSoon(title: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("قريبًا في نسخة أندرويد", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
