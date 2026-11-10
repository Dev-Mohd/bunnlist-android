package com.bunnlist.app.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.FounderBadge
import com.bunnlist.core.FounderStats
import com.bunnlist.core.SocialNetwork
import com.bunnlist.core.Socials
import kotlinx.coroutines.launch
import java.io.File

// «مبدع مؤسس»: the seal, the chip next to a name, the gold ring on a picture, the sheet that
// explains it, the founder's linked accounts, editing them, and a card to share the badge.

private val SealLight = Color(0xFFF6DD9E)
private val SealMid = Color(0xFFD9A94A)
private val SealDark = Color(0xFF98702A)
private val ChipBg = Color(0xFFF5E7C6)
private val ChipInk = Color(0xFF6E4F12)

/** The gold seal with the BunnList mark. */
@Composable
fun FounderSeal(size: Dp = 28.dp, dashed: Boolean = true) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(SealLight, SealMid, SealDark), center = Offset(this.size.width * 0.35f, this.size.height * 0.3f), radius = this.size.width * 0.7f))
            drawCircle(Color.White.copy(alpha = 0.45f), style = Stroke(width = (this.size.width * 0.03f).coerceAtLeast(1f)))
            if (dashed && size >= 40.dp) {
                val w = this.size.width
                drawCircle(Brand.Brown.copy(alpha = 0.35f), radius = w * 0.4f,
                    style = Stroke(width = (w * 0.015f).coerceAtLeast(1f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.03f, w * 0.03f))))
            }
        }
        BrandMark(height = size * 0.5f, animated = false)
    }
}

/** «مبدع مؤسس · #07», or «مؤسس» where space is tight. Tapping opens the explainer. */
@Composable
fun FounderChip(number: Int, compact: Boolean = false, name: String? = null, isMine: Boolean = false) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.clip(CircleShape).background(ChipBg).clickable { open = true }
        .padding(start = if (compact) 4.dp else 6.dp, end = if (compact) 8.dp else 11.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FounderSeal(if (compact) 16.dp else 22.dp, dashed = false)
        Text(if (compact) " مؤسس" else " مبدع مؤسس · ${FounderBadge.label(number)}", color = ChipInk,
            fontWeight = FontWeight.Bold, fontSize = if (compact) 11.sp else 13.sp)
    }
    if (open) FounderSheet(number, name, isMine) { open = false }
}

/** A picture with a gold ring and, when big enough, a small seal at its corner. */
@Composable
fun FounderAvatar(url: String?, name: String?, size: Dp, founder: Boolean) {
    if (!founder) { Avatar(url, name, size); return }
    Box(Modifier.size(size + 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size + 6.dp).border((size.value * 0.035f).coerceAtLeast(2f).dp, SealMid, CircleShape).padding(3.dp)) { Avatar(url, name, size) }
        if (size >= 44.dp) Box(Modifier.align(Alignment.BottomStart).border(2.dp, MaterialTheme.colorScheme.background, CircleShape)) {
            FounderSeal(size * 0.36f, dashed = false)
        }
    }
}

/** A founder's accounts elsewhere, as small pills that open the account. */
@Composable
fun SocialLinks(socials: Socials?) {
    val links = socials?.linked.orEmpty()
    if (links.isEmpty()) return
    val uri = LocalUriHandler.current
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        links.forEach { (network, handle) ->
            Row(Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable { runCatching { uri.openUri(network.url(handle)) } }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                SocialTile(network, 20.dp)
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(" $handle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/** Two letters on a coloured tile for a network (we don't draw their logos). */
@Composable
fun SocialTile(network: SocialNetwork, size: Dp = 20.dp) {
    val bg: Brush = when (network) {
        SocialNetwork.TikTok, SocialNetwork.X -> Brush.linearGradient(listOf(Color.Black, Color.Black))
        SocialNetwork.Instagram -> Brush.linearGradient(listOf(Color(0xFFF5A623), Color(0xFFD6249F), Color(0xFF6A3DE8)))
        SocialNetwork.YouTube -> Brush.linearGradient(listOf(Color.Red, Color.Red))
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(bg), contentAlignment = Alignment.Center) {
        Text(network.mark, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp)
    }
}

/** What the badge is: number of 100, places left, perks, and for the founder, a card to share. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FounderSheet(number: Int, name: String?, isMine: Boolean, onDismiss: () -> Unit) {
    val app = LocalContext.current.applicationContext as? BunnListApp
    var stats by remember { mutableStateOf<FounderStats?>(null) }
    LaunchedEffect(Unit) { stats = app?.let { a -> runCatching { a.api.founderStats() }.getOrNull() } }
    val limit = stats?.limit ?: 100
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FounderSeal(140.dp)
            Text("مبدع مؤسس", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("رقم $number من $limit", color = Tone.gold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("من أوائل المبدعين اللي بنوا BunnList معنا. الشارة دائمة، وما تنعطى بعد أول $limit.",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            stats?.let { s ->
                Column(Modifier.fillMaxWidth()) {
                    LinearProgressIndicator(progress = { s.progress }, color = SealMid, modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape))
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("${s.taken} مؤسس انضموا", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text(if (s.isFull) "اكتمل المؤسسون" else "باقي ${s.remaining} مكان", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            listOf("وصفاتك تطلع باسمك، ومعها كم شخص حضّرها", "روابط حساباتك (تيك توك، إنستقرام…) في ملفك", "نبرزك في حسابات BunnList")
                .forEachIndexed { i, t ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(ChipBg), contentAlignment = Alignment.Center) { Text("${i + 1}", color = Tone.gold, fontWeight = FontWeight.Bold) }
                        Text("  $t", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                }
            if (isMine) FounderShare(number, name, limit)
        }
    }
}

/** The founder's card (story size when shared), drawn on screen and shared as an image. */
@Composable
private fun FounderShare(number: Int, name: String?, limit: Int) {
    val context = LocalContext.current
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    Box(Modifier.padding(top = 8.dp).size(216.dp, 384.dp).drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }
        .clip(RoundedCornerShape(18.dp)).background(Color(0xFFF7F1E8))
        .drawWithContent { drawRect(Brush.radialGradient(listOf(SealDark.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width / 2, size.height * 0.32f), radius = size.width)); drawContent() },
        contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FounderSeal(96.dp)
            Text("مبدع مؤسس", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Brand.Ink)
            Text("رقم $number من $limit", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Brand.Gold)
            name?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Brand.Brown) }
            Spacer(Modifier.height(24.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(verticalAlignment = Alignment.CenterVertically) { BrandMark(18.dp, animated = false); Text(" BunnList", fontWeight = FontWeight.Black, fontSize = 15.sp, color = Brand.Ink) }
            }
            Text("دليل القهوة المختصة", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Brand.Gold)
        }
    }
    Button(onClick = {
        scope.launch {
            val bmp = layer.toImageBitmap().asAndroidBitmap()
            val dir = File(context.cacheDir, "shares").apply { mkdirs() }
            val file = File(dir, "bunnlist-founder-$number.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_TEXT, "صرت مبدع مؤسس ${FounderBadge.label(number)} في BunnList ☕ https://bunnlist.com")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null))
        }
    }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
        Icon(Icons.Filled.Share, null); Text(" شارك شارتك", fontWeight = FontWeight.Bold)
    }
}

/** Founders link their accounts elsewhere; each field takes a handle or a profile link. */
@Composable
fun FounderSocialsScreen(app: BunnListApp, nav: Nav) {
    var me by remember { mutableStateOf<com.bunnlist.core.Me?>(null) }
    val scope = rememberCoroutineScope()
    val values = remember { mutableStateMapOf<SocialNetwork, String>() }
    LaunchedEffect(Unit) {
        runCatching { app.session.withToken { app.api.me(it) } }.onSuccess { m ->
            me = m; SocialNetwork.entries.forEach { values[it] = m.socials?.get(it).orEmpty() }
        }
    }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    fun invalid(n: SocialNetwork) = values[n].orEmpty().isNotBlank() && n.handleFrom(values[n].orEmpty()) == null
    Column(Modifier.fillMaxSize()) {
        TopBar("حساباتك", onBack = { nav.pop() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            me?.founder?.let { f ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFFFBF3DF)).border(1.dp, Color(0xFFEBD39C), RoundedCornerShape(18.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    FounderSeal(48.dp, dashed = false)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text("أنت مبدع مؤسس ${f.label}", fontWeight = FontWeight.Bold, color = Brand.Ink)
                        Text("حساباتك تطلع في ملفك وعلى وصفاتك.", fontSize = 13.sp, color = Color(0xFF6B6258))
                    }
                }
            }
            SocialNetwork.entries.forEach { n ->
                OutlinedTextField(values[n].orEmpty(), { values[n] = it }, label = { Text(n.nameAr) }, singleLine = true,
                    leadingIcon = { SocialTile(n, 26.dp) }, isError = invalid(n),
                    supportingText = { if (invalid(n)) Text("اكتب اسم الحساب أو رابطه") },
                    placeholder = { Text("@اسم الحساب أو الرابط") }, modifier = Modifier.fillMaxWidth())
            }
            message?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            Button(onClick = {
                scope.launch {
                    saving = true
                    val cleaned = SocialNetwork.entries.associateWith { n -> values[n].orEmpty().trim().let { t -> if (t.isEmpty()) "" else n.handleFrom(t) ?: t } }
                    val ok = runCatching { app.session.withToken { app.api.updateSocials(it, cleaned) } }
                    saving = false
                    if (ok.isSuccess) nav.pop() else message = "تعذّر الحفظ. تأكد من أسماء الحسابات وحاول مرة ثانية."
                }
            }, enabled = !saving && SocialNetwork.entries.none { invalid(it) }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = CircleShape) {
                Text("حفظ", fontWeight = FontWeight.Bold)
            }
        }
    }
}
