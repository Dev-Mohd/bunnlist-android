package com.bunnlist.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp

@Composable
fun AccountScreen(app: BunnListApp, signedIn: Boolean, onSignOut: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("حسابي", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(if (signedIn) "مسجّل دخول" else "تتصفّح كضيف", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) { Text(if (signedIn) "تسجيل الخروج" else "سجّل دخولك أو أنشئ حساب") }
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
