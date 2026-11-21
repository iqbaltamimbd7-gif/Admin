package com.sayma.builders.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.SaymaApp
import com.sayma.builders.R
import com.sayma.builders.data.Api
import com.sayma.builders.data.ApiException
import com.sayma.builders.data.FileData
import com.sayma.builders.data.FilePart
import kotlinx.coroutines.launch

val LocalUserDisplayName = compositionLocalOf { "Admin" }
val LocalUserProfilePhoto = compositionLocalOf<String?> { null }

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Failed(val message: String, val authRequired: Boolean = false) : UiState<Nothing>
    data class Ready<T>(val value: T) : UiState<T>
}

class Reload(val trigger: () -> Unit)

@Composable
fun <T> useLoad(key: Any? = Unit, onAuthLost: () -> Unit = {}, block: suspend () -> T): Pair<UiState<T>, Reload> {
    var version by remember { mutableIntStateOf(0) }
    val state = produceState<UiState<T>>(UiState.Loading, key, version) {
        value = UiState.Loading
        value = try { UiState.Ready(block()) } catch (e: ApiException) {
            if (e.authRequired) onAuthLost()
            UiState.Failed(e.message ?: "ত্রুটি", e.authRequired)
        } catch (e: Exception) { UiState.Failed(e.message ?: "ত্রুটি") }
    }
    return state.value to Reload { version++ }
}

@Composable
fun apiOf(): Api = (androidx.compose.ui.platform.LocalContext.current.applicationContext as SaymaApp).api

@Composable
private fun LoadingPanel() {
    val transition = rememberInfiniteTransition(label = "sayma-loading")
    val pulse by transition.animateFloat(
        initialValue = 0.34f,
        targetValue = 0.82f,
        animationSpec = infiniteRepeatable(animation = tween(950), repeatMode = RepeatMode.Reverse),
        label = "sayma-loading-pulse"
    )
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFF4ECFA), Color(0xFFEAF7F5), Color(0xFFFFF0F4))))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Orchid, strokeWidth = 2.5.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("SAYMA", color = Ink, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, letterSpacing = 1.sp)
                Text("তথ্য প্রস্তুত হচ্ছে…", color = Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(12.dp))
        repeat(2) {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(bottom = 8.dp)
                    .height(54.dp).clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFEDE7F5).copy(alpha = pulse))
            )
        }
    }
}

@Composable
fun <T> Loaded(state: UiState<T>, reload: Reload, content: @Composable (T) -> Unit) {
    when (state) {
        UiState.Loading -> LoadingPanel()
        is UiState.Failed -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.message, color = Bad, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = reload.trigger) { Text("আবার চেষ্টা করুন") }
        }
        is UiState.Ready -> content(state.value)
    }
}

@Composable
fun SaymaCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    val fill = Brush.linearGradient(listOf(Color.White.copy(alpha = .97f), Color(0xFFFCF7FB), Color(0xFFF0F8F6)))
    val edge = Brush.linearGradient(listOf(Mint.copy(alpha = .38f), Orchid.copy(alpha = .30f), Rose.copy(alpha = .28f)))
    val m = modifier.fillMaxWidth().shadow(8.dp, shape).clip(shape).background(fill)
        .border(BorderStroke(1.dp, edge), shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Column(m.padding(15.dp), content = content)
}

@Composable
fun ProfileAvatar(name: String, size: Int = 40, photoData: String? = LocalUserProfilePhoto.current) {
    val api = apiOf()
    val raw = photoData.orEmpty().trim()
    val looksBase64 = raw.startsWith("data:image", true) ||
        (raw.length > 100 && Regex("^[A-Za-z0-9+/=\\r\\n]+$").matches(raw))
    val remote = raw.isNotBlank() && !looksBase64
    val (remoteState, _) = useLoad(if (remote) raw else "") {
        if (remote) api.profilePhotoBytes(raw) else null
    }
    val bytes = if (remote) {
        (remoteState as? UiState.Ready<ByteArray?>)?.value
    } else {
        try {
            val encoded = if (raw.contains("base64,")) raw.substringAfter("base64,") else raw
            if (encoded.isBlank()) null else Base64.decode(encoded, Base64.DEFAULT)
        } catch (_: Exception) {
            try { Base64.decode(raw, Base64.URL_SAFE) } catch (_: Exception) { null }
        }
    }
    val bitmap: Bitmap? = remember(raw, bytes) { bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
    if (bitmap != null) {
        Image(bitmap.asImageBitmap(), contentDescription = "$name profile photo",
            modifier = Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    } else Avatar(name, size)
}

@Composable
fun BrandHeader(profileName: String = LocalUserDisplayName.current) {
    val displayName = profileName.substringBefore("@").take(15).ifBlank { "Admin" }
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp)
            .shadow(8.dp, shape).clip(shape)
            .background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .96f), Color(0xFFF7EAF3).copy(alpha = .96f))))
            .border(BorderStroke(1.dp, Brush.horizontalGradient(listOf(Mint.copy(alpha = .42f), Orchid.copy(alpha = .34f)))), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.sayma_logo), "SAYMA BUILDERS", Modifier.size(42.dp).clip(CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("SAYMA", color = Teal, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(" BUILDERS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Text("CONSTRUCTION MANAGEMENT", color = Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, maxLines = 1)
        }
        Row(
            Modifier.clip(CircleShape).background(Color.White.copy(alpha = .78f))
                .border(1.dp, Orchid.copy(alpha = .16f), CircleShape)
                .padding(start = 5.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfileAvatar(displayName, 32)
            Spacer(Modifier.width(7.dp))
            Column {
                Text(displayName, color = Ink, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("● Connected", color = Warn, fontSize = 9.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String = "",
    action: (@Composable () -> Unit)? = null,
    profileName: String = LocalUserDisplayName.current
) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        BrandHeader(profileName)
        Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
            Text(
                if (title.startsWith("Hi,")) "YOUR SAYMA WORKSPACE" else "SAYMA BUILDERS SDN BHD",
                color = Orchid, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.35.sp
            )
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = Ink, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
                action?.invoke()
            }
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
fun SearchPill(value: String, onChange: (String) -> Unit, placeholder: String = "Search workers, passport, project...") {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(19.dp))
            .background(Color.White.copy(alpha = .92f))
            .border(1.dp, Mint.copy(alpha = .20f), RoundedCornerShape(19.dp))
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Search, contentDescription = "Search", tint = Teal, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (value.isBlank()) Text(placeholder, color = Muted, fontSize = 13.sp)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Ink, fontSize = 13.sp)
            )
        }
        if (value.isNotBlank()) {
            Text("×", color = Muted, fontSize = 20.sp, modifier = Modifier.clickable { onChange("") }.padding(start = 8.dp))
        } else {
            Text("⌘ K", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Color(0xFFE6F4F1)).padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

data class SaymaNavItem(val label: String, val icon: ImageVector)

@Composable
fun SaymaBottomBar(items: List<SaymaNavItem>, selected: String, onSelect: (String) -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
        color = Color.White.copy(alpha = .96f), shape = shape,
        border = BorderStroke(1.dp, Color.White), shadowElevation = 12.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items.forEach { item ->
                val active = item.label == selected
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable { onSelect(item.label) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(13.dp)).background(if (active) Color(0xFFE9DDF8) else Color.Transparent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(item.icon, contentDescription = item.label, tint = if (active) Color(0xFF9A45B8) else Color(0xFF8D91A0), modifier = Modifier.size(21.dp))
                    }
                    Text(item.label, color = if (active) Color(0xFF9A45B8) else Color(0xFF858A99), fontSize = 9.sp, fontWeight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun GradientButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled, modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal)
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

@Composable
fun SoftButton(label: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Teal.copy(alpha = .35f))) {
        Text(label, color = Teal, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun LabeledField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, password: Boolean = false, multiline: Boolean = false, keyboard: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, modifier = modifier.fillMaxWidth(),
        singleLine = !multiline, minLines = if (multiline) 3 else 1, shape = RoundedCornerShape(14.dp),
        visualTransformation = if (password) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (password) androidx.compose.ui.text.input.KeyboardType.Password else keyboard)
    )
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .13f)).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

fun statusColor(s: String): Color = when (s.lowercase()) {
    "approved", "active", "completed", "yes" -> Good
    "rejected", "no" -> Bad
    "pending", "processing", "working" -> Warn
    else -> Muted
}

@Composable
fun Avatar(text: String, size: Int = 40) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Brush2), contentAlignment = Alignment.Center) {
        Text(text.trim().take(1).uppercase().ifBlank { "?" }, color = Color.White, fontWeight = FontWeight.ExtraBold)
    }
}
private val Brush2 = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Mint, Rose, Orchid))

@Composable
fun ListRow(title: String, subtitle: String = "", trailing: String = "", trailingSub: String = "", avatar: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (avatar != null) { Avatar(avatar); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing.isNotBlank()) Column(horizontalAlignment = Alignment.End) {
            Text(trailing, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            if (trailingSub.isNotBlank()) Text(trailingSub, color = Muted, fontSize = 11.sp)
        }
    }
    HorizontalDivider(color = Line)
}

@Composable
fun EmptyState(title: String, desc: String = "") {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("▧", fontSize = 28.sp, color = Teal.copy(alpha = .5f))
        Text(title, fontWeight = FontWeight.Bold)
        if (desc.isNotBlank()) Text(desc, color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, color: Color = Teal) {
    val shape = RoundedCornerShape(17.dp)
    Column(
        modifier.shadow(4.dp, shape).clip(shape)
            .background(Brush.linearGradient(listOf(color.copy(alpha = .15f), Color.White, Color(0xFFF9F5FC))))
            .border(BorderStroke(1.dp, color.copy(alpha = .16f)), shape)
            .padding(12.dp)
    ) {
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = color)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
fun ProgressBar(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Teal))
    }
}

/** Helper that opens a private server file inside the native viewer. */
class FileOpener(val open: (suspend () -> FileData) -> Unit)

@Composable
fun rememberFileOpener(onError: (String) -> Unit = {}): Pair<FileOpener, @Composable () -> Unit> {
    val scope = rememberCoroutineScope()
    var file by remember { mutableStateOf<FileData?>(null) }
    var busy by remember { mutableStateOf(false) }
    val opener = FileOpener { loader ->
        scope.launch {
            busy = true
            try { file = loader() } catch (e: Exception) { onError(e.message ?: "ফাইল খোলা যায়নি") }
            busy = false
        }
    }
    val host: @Composable () -> Unit = {
        if (busy) Dialog0()
        file?.let { FileViewer(it) { file = null } }
    }
    return opener to host
}

@Composable
private fun Dialog0() {
    androidx.compose.ui.window.Dialog(onDismissRequest = {}) {
        Box(Modifier.size(90.dp).clip(RoundedCornerShape(18.dp)).background(Color.White), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Teal) }
    }
}

fun readUri(context: Context, uri: Uri, field: String): FilePart? {
    val resolver = context.contentResolver
    var name = "upload"
    resolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (c.moveToFirst() && i >= 0) name = c.getString(i) ?: name
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return FilePart(field, name, resolver.getType(uri) ?: "application/octet-stream", bytes)
}
