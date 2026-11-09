package com.bunnlist.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bunnlist.app.BunnListApp
import com.bunnlist.app.R
import com.bunnlist.core.ApiError
import kotlinx.coroutines.launch

private data class IntroCard(val image: Int, val title: String, val detail: String)

private val introCards = listOf(
    IntroCard(R.drawable.intro_discover, "اكتشف بذوقك", "أكثر من ٧٠٠ قهوة مختصة من محامص سعودية، و«ساعدني أختار» يرشّح لك على ذوقك."),
    IntroCard(R.drawable.intro_brew, "حضّرها صح", "وصفات دقيقة لكل طريقة، ومؤقت يمشي معك صبّة صبّة."),
    IntroCard(R.drawable.intro_collection, "مجموعتك ورحلتك", "سجّل قهاويك، وجمّع جوازك، وتابع الذوّاقة في المجتمع."),
)

/**
 * The landing screen (as iOS WelcomeView): the mark draws itself, then three swipeable cards show what the
 * app does, with Google, email or guest always below. While signing in the whole screen becomes the drawing mark.
 */
@Composable
fun WelcomeScreen(app: BunnListApp, onSignedIn: () -> Unit, onGuest: () -> Unit) {
    var showEmail by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pager = rememberPagerState { introCards.size + 1 }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().brandBackdrop(MaterialTheme.colorScheme.background).systemBarsPadding()) {
            HorizontalPager(pager, Modifier.weight(1f)) { page ->
                if (page == 0) BrandPage() else IntroCardView(introCards[page - 1])
            }
            PageDots(introCards.size + 1, pager.currentPage, Modifier.align(Alignment.CenterHorizontally).padding(vertical = 10.dp))
            Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (GoogleSignIn.isConfigured) AuthButton("المتابعة بحساب Google", filled = true, icon = { GoogleMark() }) {
                    busy = true; error = null
                    scope.launch {
                        when (val r = GoogleSignIn.signIn(context, app)) {
                            GoogleSignIn.Outcome.Success -> onSignedIn()
                            GoogleSignIn.Outcome.Cancelled -> {}
                            is GoogleSignIn.Outcome.Failed -> error = r.messageAr
                        }
                        busy = false
                    }
                }
                AuthButton("المتابعة بالإيميل", filled = false, icon = { Icon(Icons.Filled.Email, null, Modifier.size(20.dp)) }) { showEmail = true }
                Text("تصفّح كضيف", fontWeight = FontWeight.SemiBold, color = Tone.gold, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).pressable(onClick = onGuest)
                    .wrapContentHeight(Alignment.CenterVertically), textAlign = TextAlign.Center)
                error?.let { Text("⚠ $it", color = MaterialTheme.colorScheme.error, fontSize = 13.sp, textAlign = TextAlign.Center) }
                Text("بالمتابعة توافق على الشروط وسياسة الخصوصية.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (busy) BrandLoadingView("نجهّز حسابك…")
    }
    if (showEmail) EmailSignInSheet(app, onDismiss = { showEmail = false }, onSignedIn = { showEmail = false; onSignedIn() })
}

@Composable
private fun BrandPage() {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Spacer(Modifier.weight(1f))
        BrandMark(130.dp)
        BrandWordmark(32)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("اكتشف، قيّم، واختر أفضل محاصيل القهوة", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text("Rate Coffee. Brew Smarter.", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Swipe, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(" اسحب وتعرّف على التطبيق", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** One card of the tour: a real screen from the app, a title and a line. */
@Composable
private fun IntroCardView(card: IntroCard) {
    Column(Modifier.fillMaxSize().padding(horizontal = 32.dp).padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Image(painterResource(card.image), null, contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).shadow(18.dp, RoundedCornerShape(28.dp), ambientColor = Brand.Ink.copy(alpha = 0.15f), spotColor = Brand.Ink.copy(alpha = 0.15f))
                .clip(RoundedCornerShape(28.dp)).border(1.dp, Brand.Ink.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                // The shot is the top of a screen; fade its cut edge into the page.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent { drawContent(); drawRect(Brush.verticalGradient(0.8f to Color.Black, 1f to Color.Transparent), blendMode = BlendMode.DstIn) })
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(card.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(card.detail, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            val w by animateDpAsState(if (i == current) 20.dp else 7.dp, spring(dampingRatio = 0.7f), label = "dot$i")
            Box(Modifier.size(w, 7.dp).clip(CircleShape).background(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)))
        }
    }
}

/** One full-width sign-in button: same height, corners and type for every provider. */
@Composable
private fun AuthButton(title: String, filled: Boolean, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onBackground; val bg = MaterialTheme.colorScheme.background; val olive = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(16.dp)).background(if (filled) ink else bg)
        .then(if (filled) Modifier else Modifier.border(1.5.dp, olive.copy(alpha = 0.55f), RoundedCornerShape(16.dp))).pressable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides if (filled) bg else olive) { icon() }
        Text("  $title", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (filled) bg else olive)
    }
}

/** Email sign-in: send a 6-digit code, then verify it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EmailSignInSheet(app: BunnListApp, onDismiss: () -> Unit, onSignedIn: () -> Unit) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var sent by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val validEmail = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email.trim())
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (sent) "اكتب الكود" else "الدخول بالإيميل", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!sent) OutlinedTextField(email, { email = it }, label = { Text("الإيميل") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
            else {
                Text("أرسلنا كود من ٦ أرقام إلى ${email.trim()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(code, { if (it.length <= 6 && it.all(Char::isDigit)) code = it }, label = { Text("الكود") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.fillMaxWidth())
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Button(enabled = !busy && (if (sent) code.length == 6 else validEmail), onClick = {
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
            }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground, contentColor = MaterialTheme.colorScheme.background)) {
                Text(if (busy) "لحظة…" else if (sent) "تحقّق" else "أرسل الكود", fontSize = 17.sp)
            }
            if (sent) TextButton(onClick = { sent = false; code = "" }, modifier = Modifier.fillMaxWidth()) { Text("غيّر الإيميل") }
        }
    }
}
