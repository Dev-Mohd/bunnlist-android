package com.bunnlist.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.app.R
import com.bunnlist.core.ApiError
import kotlinx.coroutines.launch

@Composable
fun WelcomeScreen(app: BunnListApp, onSignedIn: () -> Unit, onGuest: () -> Unit) {
    var showEmail by remember { mutableStateOf(false) }
    var googleBusy by remember { mutableStateOf(false) }
    var googleError by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Image(painterResource(R.mipmap.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(150.dp))
            Text("BunnList", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("اكتشف، قيّم، واختر أفضل محاصيل القهوة", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            if (showEmail) {
                EmailSignIn(app, onSignedIn = onSignedIn, onCancel = { showEmail = false })
            } else {
                if (GoogleSignIn.isConfigured) {
                    OutlinedButton(enabled = !googleBusy, onClick = {
                        googleBusy = true; googleError = null
                        scope.launch {
                            when (val r = GoogleSignIn.signIn(context, app)) {
                                GoogleSignIn.Outcome.Success -> onSignedIn()
                                GoogleSignIn.Outcome.Cancelled -> {}
                                is GoogleSignIn.Outcome.Failed -> googleError = r.messageAr
                            }
                            googleBusy = false
                        }
                    }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                        Text("G", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = androidx.compose.ui.graphics.Color(0xFF4285F4))
                        Text(if (googleBusy) "  لحظة…" else "  المتابعة بحساب Google", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                    googleError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center) }
                    Spacer(Modifier.height(10.dp))
                }
                Button(onClick = { showEmail = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("المتابعة بالإيميل", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onGuest, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("تصفّح كضيف", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
                }
                Text("بالمتابعة توافق على الشروط وسياسة الخصوصية.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Email sign-in: send a 6-digit code, then verify it. Same server flow as iOS. */
@Composable
private fun EmailSignIn(app: BunnListApp, onSignedIn: () -> Unit, onCancel: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val validEmail = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())

    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!sent) {
            OutlinedTextField(email, { email = it }, label = { Text("الإيميل") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        } else {
            Text("أرسلنا كود من ٦ أرقام إلى ${email.trim()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(code, { if (it.length <= 6 && it.all(Char::isDigit)) code = it }, label = { Text("الكود") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
        Button(
            enabled = !busy && (if (sent) code.length == 6 else validEmail),
            onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        if (!sent) { app.api.requestEmailCode(email); sent = true }
                        else { app.session.adopt(app.api.verifyEmailCode(email, code, app.deviceId)); onSignedIn() }
                    } catch (e: ApiError) {
                        error = when (e) {
                            ApiError.RateLimited -> "محاولات كثيرة، جرّب بعد شوي."
                            ApiError.Unauthorized, is ApiError.Unprocessable -> if (sent) "الكود غلط أو انتهى، تأكد منه." else "تأكد من الإيميل."
                            is ApiError.Network -> "تأكد من اتصالك بالإنترنت."
                            else -> "صار خطأ، جرّب مرة ثانية."
                        }
                    } finally { busy = false }
                }
            },
            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
        ) { Text(if (busy) "لحظة…" else if (sent) "تحقّق" else "أرسل الكود", fontSize = 17.sp) }
        TextButton(onClick = { if (sent) { sent = false; code = "" } else onCancel() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (sent) "غيّر الإيميل" else "رجوع")
        }
    }
}
