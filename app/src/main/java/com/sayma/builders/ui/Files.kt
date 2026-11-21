package com.sayma.builders.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

/** Native in-app viewer: PdfRenderer for PDFs, BitmapFactory for images. No WebView. */
@Composable
fun FileViewer(file: FileData, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                Surface(color = Teal) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(file.name, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { scope.launch { snack.showSnackbar(saveToDownloads(context, file)) } }) { Text("Download", color = androidx.compose.ui.graphics.Color.White) }
                        TextButton(onClick = onClose) { Text("Close", color = androidx.compose.ui.graphics.Color.White) }
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when {
                    file.isPdf -> PdfPages(file.bytes)
                    file.isImage -> {
                        val bmp = remember(file) { BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size) }
                        if (bmp != null) Image(bmp.asImageBitmap(), file.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                        else Centered("ছবি দেখানো যায়নি। Download ব্যবহার করুন।")
                    }
                    else -> Centered("এই ফাইলের ধরন অ্যাপে দেখানো যায় না। Download করে খুলুন।")
                }
            }
        }
    }
}

private fun Modifier: Modifier = this.then(Modifier.fillMaxSize())

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
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp)) {
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
                bmp?.let { Image(it.asImageBitmap(), "Page ${index + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                    ?: Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        }
    }
}
