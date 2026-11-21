package com.sayma.builders.ui.worker

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import com.sayma.builders.data.*
import com.sayma.builders.ui.*
import org.json.JSONObject

private enum class WTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home), Payslips("Payslips", Icons.Filled.Receipt),
    Clock("Attendance", Icons.Filled.AccessTime), More("More", Icons.Filled.Menu)
}

private enum class WScreen { Payments, Leave, Bank }

@Composable
fun WorkerApp(name: String, onLogout: () -> Unit) {
    val api = apiOf()
    var tab by remember { mutableStateOf(WTab.Home) }
    var screen by remember { mutableStateOf<WScreen?>(null) }
    val (state, reload) = useLoad(Unit, onAuthLost = onLogout) { api.get("worker_dashboard") }
    BackHandler(enabled = screen != null) { screen = null }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (screen == null) SaymaBottomBar(
                items = WTab.entries.map { SaymaNavItem(it.label, it.icon) },
                selected = tab.label,
                onSelect = { label -> tab = WTab.entries.first { it.label == label } }
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(ScreenBrush).statusBarsPadding()) {
            when (screen) {
                WScreen.Payments -> Loaded(state, reload) { PaymentsScreen(it, reload) { screen = null } }
                WScreen.Leave -> LeaveScreen { screen = null }
                WScreen.Bank -> BankScreen { screen = null }
                null -> when (tab) {
                    WTab.Home -> Loaded(state, reload) { HomeTab(it, name) { target -> screen = target } }
                    WTab.Payslips -> PayslipsScreen()
                    WTab.Clock -> AttendanceScreen()
                    WTab.More -> MoreTab(name, { screen = it }, onLogout)
                }
            }
        }
    }
}

@Composable
private fun MoreTab(name: String, open: (WScreen) -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader("Workspace", "Your worker tools and records", profileName = name)
        SaymaCard {
            ListRow("Payments", "Submit & track payment confirmations", "›", "", onClick = { open(WScreen.Payments) })
            ListRow("Leave requests", "Apply for leave and attach MC", "›", "", onClick = { open(WScreen.Leave) })
            ListRow("Bank account", "Apply for RHB / CIMB account", "›", "", onClick = { open(WScreen.Bank) })
        }
        Spacer(Modifier.height(14.dp))
        GradientButton("Sign out", Modifier.fillMaxWidth(), onClick = onLogout)
    }
}

@Composable
private fun ProfilePhoto(worker: JSONObject, size: Int = 64) {
    val api = apiOf()
    val url = worker.str("photo_url")
    val (state, _) = useLoad(url) {
        when {
            url.isBlank() -> null
            url.startsWith("data:image") -> android.util.Base64.decode(url.substringAfter("base64,"), android.util.Base64.DEFAULT)
            url.contains("worker_view_profile_photo") -> api.file("worker_view_profile_photo", emptyMap(), "photo.jpg").bytes
            else -> null
        }
    }
    val bmp = (state as? UiState.Ready<ByteArray?>)?.value?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    if (bmp != null) Image(bmp.asImageBitmap(), "Profile", Modifier.size(size.dp).clip(CircleShape), contentScale = ContentScale.Crop)
    else Avatar(worker.str("name"), size)
}

@Composable
private fun HomeTab(d: JSONObject, fallbackName: String, open: (WScreen) -> Unit) {
    val w = d.optJSONObject("worker") ?: JSONObject()
    val ledger = d.optJSONObject("payments") ?: JSONObject()
    val years = ledger.keys().asSequence().sortedDescending().toList()
    val subs = d.optJSONArray("submissions").objects()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 20.dp)) {
        ScreenHeader("Hi, ${w.str("name").ifBlank { fallbackName }.substringBefore(' ')}.", "Worker portal", profileName = w.str("name").ifBlank { fallbackName })
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(HeroBrush).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfilePhoto(w)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(w.str("name"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("${w.str("role")} · ${w.str("site").ifBlank { "No site assigned" }}", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
                Text("Passport ${w.str("passport")}", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
            }
        }
        SectionTitle("Quick actions")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SaymaCard(Modifier.weight(1f), onClick = { open(WScreen.Payments) }) { Text("Submit payment", fontWeight = FontWeight.Bold, color = Teal, fontSize = 13.sp) }
            SaymaCard(Modifier.weight(1f), onClick = { open(WScreen.Leave) }) { Text("Apply leave", fontWeight = FontWeight.Bold, color = Teal, fontSize = 13.sp) }
        }
        SectionTitle("Payment ledger")
        if (years.isEmpty()) SaymaCard { EmptyState("No payment record yet") }
        years.forEach { y ->
            val rec = ledger.optJSONObject(y) ?: return@forEach
            val target = rec.num("target"); val paid = rec.num("paid")
            SaymaCard(Modifier.padding(bottom = 8.dp)) {
                Text("$y", fontWeight = FontWeight.ExtraBold)
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Target", rm(target), Modifier.weight(1f)); StatTile("Paid", rm(paid), Modifier.weight(1f), Good); StatTile("Left", rm(Math.max(0.0, target - paid)), Modifier.weight(1f), Rose)
                }
                ProgressBar(if (target > 0) (paid / target).toFloat() else 0f)
                rec.optJSONArray("costItems").objects().forEach { ci -> Row(Modifier.padding(top = 6.dp)) { Text(ci.str("part"), Modifier.weight(1f), fontSize = 13.sp); Text(rm(ci.num("amount")), fontSize = 13.sp) } }
            }
        }
        SectionTitle("Recent submissions")
        SaymaCard {
            if (subs.isEmpty()) EmptyState("No submissions yet")
            subs.take(5).forEach { s -> ListRow(rm(s.num("amount")), s.str("payment_date") + if (s.str("note").isNotBlank()) " · " + s.str("note") else "", s.str("status").replaceFirstChar { it.uppercase() }, "") }
        }
    }
}
