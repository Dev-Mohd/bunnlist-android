package com.bunnlist.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.bunnlist.app.BunnListApp
import com.bunnlist.core.ApiError
import com.bunnlist.core.BagScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** Standing in front of a bag: photograph it and we find the coffee. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanBagSheet(app: BunnListApp, signedIn: Boolean, onDismiss: () -> Unit, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BagScanResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val photoUri = remember {
        val dir = File(context.cacheDir, "scans").apply { mkdirs() }
        FileProvider.getUriForFile(context, context.packageName + ".files", File(dir, "bag.jpg"))
    }
    fun scan(uri: Uri) {
        busy = true; error = null; result = null
        scope.launch {
            try {
                val b64 = withContext(Dispatchers.Default) { encode(context, uri) } ?: throw IllegalStateException()
                result = app.session.withToken { app.api.scanBag(b64, it) }
            } catch (e: ApiError) {
                error = when (e) {
                    ApiError.RateLimited -> "وصلت حد المسح لليوم، جرّب بكرة أو ابحث بالاسم."
                    is ApiError.Network -> "تأكد من اتصالك بالإنترنت."
                    else -> "ما قدرنا نقرا الكيس، جرّب صورة أوضح."
                }
            } catch (e: Exception) { error = "ما قدرنا نقرا الصورة." }
            busy = false
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) scan(photoUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { scan(it) } }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("امسح كيس القهوة", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            if (!signedIn) {
                Text("سجّل دخولك عشان تقدر تمسح الأكياس.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("صوّر الملصق الأمامي بوضوح، ونطلع لك القهوة من الدليل.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !busy, onClick = { camera.launch(photoUri) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PhotoCamera, null); Text("  صوّر")
                }
                OutlinedButton(enabled = !busy, onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PhotoLibrary, null); Text("  من الصور")
                }
            }
            if (busy) Row { CircularProgressIndicator(Modifier.size(22.dp)); Text("  نقرا الكيس…") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            result?.let { r ->
                if (r.extraction.summary.isNotBlank()) Text("قرينا: ${r.extraction.summary}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r.matches.isEmpty()) Text("ما لقيناها في الدليل للحين. جرّب البحث بالاسم.", textAlign = TextAlign.Start)
                r.matches.take(3).forEach { lot -> LotRow(lot) { onOpen(lot.slug) } }
                Text("باقي لك ${r.remainingToday} مسح اليوم", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Longest side ≤ 1600 px, upright, JPEG 80: sharp enough for label text, small to upload. */
internal fun encode(context: Context, uri: Uri, maxSide: Int = 1600): String? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val rotation = context.contentResolver.openInputStream(uri)?.use {
        when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
        }
    } ?: 0f
    val scale = minOf(1f, maxSide.toFloat() / maxOf(raw.width, raw.height))
    val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
    val bmp = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
    return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
}
