package com.sayma.builders.ui

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.SaymaApp
import com.sayma.builders.data.Api
import com.sayma.builders.data.ApiException
import com.sayma.builders.data.FileData
import com.sayma.builders.data.FilePart
import kotlinx.coroutines.launch

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
    val shape = RoundedCornerShape(20.dp)
    val fill = Brush.linearGradient(listOf(Color.White, Color(0xFFFCF8FD), Color(0xFFF4FAFA)))
    val edge = Brush.linearGradient(listOf(Mint.copy(alpha = .32f), Orchid.copy(alpha = .26f), Rose.copy(alpha = .24f)))
    val m = modifier.fillMaxWidth().shadow(7.dp, shape).clip(shape).background(fill)
        .border(BorderStroke(1.dp, edge), shape)
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Column(m.padding(15.dp), content = content)
}

@Composable
fun ScreenHeader(title: String, subtitle: String = "", action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SAYMA BUILDERS SDN BHD", color = Teal, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (subtitle.isNotBlank()) Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        action?.invoke()
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
