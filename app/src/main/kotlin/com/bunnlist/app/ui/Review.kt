package com.bunnlist.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.core.Methods
import com.bunnlist.core.MyReview
import com.bunnlist.core.reviewMethods
import kotlinx.coroutines.launch

/** Rate a coffee: stars, how you brewed it, would you buy it again, and a few words. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewSheet(existing: MyReview?, defaultMethod: String?, onDismiss: () -> Unit, onSave: suspend (MyReview) -> String?) {
    var rating by remember { mutableIntStateOf(existing?.rating ?: 0) }
    var method by remember { mutableStateOf(existing?.brewMethod ?: defaultMethod?.takeIf { it in reviewMethods } ?: "V60") }
    var again by remember { mutableStateOf(existing?.wouldBuyAgain ?: true) }
    var body by remember { mutableStateOf(existing?.body ?: "") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(if (existing != null) "عدّل تقييمك" else "قيّمها", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                (1..5).forEach { i ->
                    IconButton(onClick = { rating = i }, modifier = Modifier.size(52.dp)) {
                        Icon(if (i <= rating) Icons.Filled.Star else Icons.Filled.StarBorder, "$i نجوم",
                            tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(40.dp))
                    }
                }
            }
            Text("حضّرتها بـ", fontWeight = FontWeight.SemiBold)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reviewMethods.forEach { m -> FilterChip(selected = method == m, onClick = { method = m }, label = { Text(if (m == "OTHER") "غيرها" else Methods.nameAr(m)) }) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("أشتريها مرة ثانية؟", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(checked = again, onCheckedChange = { again = it })
            }
            OutlinedTextField(body, { body = it.take(1000) }, placeholder = { Text("وش عجبك فيها؟ (اختياري)") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), shape = RoundedCornerShape(16.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = rating > 0 && !busy, onClick = {
                busy = true; error = null
                scope.launch { error = onSave(MyReview(rating, method, again, body)); busy = false }
            }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(50)) {
                Text(if (busy) "نحفظ…" else "احفظ التقييم", fontSize = 17.sp)
            }
        }
    }
}

