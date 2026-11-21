package com.sayma.builders.ui

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.sayma.builders.data.FileData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Saves into the phone's Downloads folder. Returns a message to show the user. */
fun saveToDownloads(context: Context, file: FileData): String {
    val safe = file.name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "SAYMA-document" }
    val mime = file.mime.ifBlank { "application/octet-stream" }
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safe)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: return "Downloads ফোল্ডারে সেভ করা যায়নি।"
            context.contentResolver.openOutputStream(uri)?.use { it.write(file.bytes) }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            File(dir, safe).writeBytes(file.bytes)
        }
        "Downloads ফোল্ডারে সেভ হয়েছে: $safe"
    } catch (e: Exception) {
        "ডাউনলোড ব্যর্থ: ${e.message}"
    }
}

/** Shares a private server document through Android's native share sheet. */
private fun shareFromApp(context: Context, file: FileData): String {
    return try {
        val safe = file.name.replace(Regex("""[\\/:*?"<>|]"""), "_").ifBlank { "SAYMA-document" }
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val cached = File(dir, safe).apply { writeBytes(file.bytes) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", cached)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = file.mime.ifBlank { "application/octet-stream" }
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, safe, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share ${file.name}").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        "Share menu opened."
    } catch (e: Exception) {
        "Share করা যায়নি: ${e.message ?: "অজানা সমস্যা"}"
    }
}

/** Native in-app viewer: PdfRenderer for PDFs, BitmapFactory for images. No WebView. */
@Composable
fun FileViewer(file: FileData, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val backdrop = Brush.linearGradient(listOf(Color(0xFFE7F1F8), Color(0xFFF4EAF6), Color(0xFFE6F4F0)))
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(Color(0xFF202F66), Color(0xFF397DAF), Color(0xFF35A78B))))) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(file.name, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { scope.launch { snack.showSnackbar(shareFromApp(context, file)) } }) { Text("Share", color = Color.White) }
                        TextButton(onClick = { scope.launch { snack.showSnackbar(saveToDownloads(context, file)) } }) { Text("Download", color = Color.White) }
                        TextButton(onClick = onClose) { Text("Close", color = Color.White) }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().background(backdrop)) {
                when {
                    file.isPdf -> PdfPages(file.bytes)
                    file.isImage -> {
                        val bmp = remember(file) { BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size) }
                        if (bmp != null) Surface(
                            modifier = Modifier.fillMaxSize().padding(14.dp),
                            shape = RoundedCornerShape(22.dp), color = Color.White, shadowElevation = 12.dp
                        ) { Image(bmp.asImageBitmap(), file.name, Modifier.fillMaxSize().padding(10.dp), contentScale = ContentScale.Fit) }
                        else Centered("ছবি দেখানো যায়নি। Download ব্যবহার করুন।")
                    }
                    else -> Surface(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        shape = RoundedCornerShape(24.dp), color = Color.White, shadowElevation = 12.dp
                    ) {
                        Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DOCUMENT PREVIEW", color = Color(0xFF397DAF), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(file.name, color = Color(0xFF202F66), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("এই ফাইলের ধরন অ্যাপে সরাসরি দেখানো যায় না। Download করে খুলুন।", color = Muted)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun Centered(text: String) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(text, color = Muted) }

@Composable
private fun PdfPages(bytes: ByteArray) {
    val context = LocalContext.current
    var pageCount by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    val renderer = remember(bytes) {
        try {
            val f = File(context.cacheDir, "view-${System.nanoTime()}.pdf").apply { writeBytes(bytes) }
            val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd).also { pageCount = it.pageCount }
        } catch (e: Exception) { error = "PDF খোলা যায়নি: ${e.message}"; null }
    }
    DisposableEffect(renderer) { onDispose { try { renderer?.close() } catch (_: Exception) {} } }
    if (renderer == null || error != null) { Centered(error ?: "PDF খোলা যায়নি।"); return }
    val lock = remember(renderer) { Any() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.roundToPx() }.coerceAtMost(1600)
        LazyColumn(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFE7F1F8), Color(0xFFF4EAF6), Color(0xFFE6F4F0)))),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp)
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DOCUMENT PREVIEW", color = Color(0xFF397DAF), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                        Text("${pageCount} ${if (pageCount == 1) "page" else "pages"}", color = Color(0xFF202F66), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Text("PDF", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF397DAF), Color(0xFF35A78B))), RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp))
                }
            }
            itemsIndexed(List(pageCount) { it }) { _, index ->
                var bmp by remember(index) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(index) {
                    bmp = withContext(Dispatchers.IO) {
                        synchronized(lock) {
                            renderer.openPage(index).use { page ->
                                val h = (widthPx.toFloat() / page.width * page.height).toInt().coerceAtLeast(1)
                                Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also {
                                    it.eraseColor(AColor.WHITE)
                                    page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                }
                            }
                        }
                    }
                }
                Column {
                    if (bmp != null) {
                        Surface(Modifier.fillMaxWidth().shadow(10.dp, RoundedCornerShape(16.dp)), shape = RoundedCornerShape(16.dp), color = Color.White) {
                            Image(bmp!!.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth().padding(8.dp), contentScale = ContentScale.FillWidth)
                        }
                    } else {
                        Surface(Modifier.fillMaxWidth().height(240.dp), shape = RoundedCornerShape(16.dp), color = Color.White, shadowElevation = 8.dp) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF397DAF)) }
                        }
                    }
                    Text("PAGE ${index + 1}", color = Color(0xFF65758D), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.End).padding(end = 6.dp, top = 3.dp))
                }
            }
        }
    }
}
