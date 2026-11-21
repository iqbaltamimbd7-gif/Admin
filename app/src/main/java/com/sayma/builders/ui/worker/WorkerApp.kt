package com.sayma.builders.ui.worker

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.data.*
import com.sayma.builders.ui.*
import org.json.JSONObject

private enum class WTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Payslips("Payslips", Icons.Filled.Receipt),
    Leave("Leave", Icons.Filled.Event),
    ClockIn("Clock-in", Icons.Filled.AccessTime),
    Profile("Profile", Icons.Filled.Person)
}

private enum class WScreen { Payments, Bank, Site }

@Composable
fun WorkerApp(name: String, onLogout: () -> Unit) {
    val api = apiOf()
    var tab by remember { mutableStateOf(WTab.Home) }
    var screen by remember { mutableStateOf<WScreen?>(null) }
    var headerPhoto by remember { mutableStateOf<String?>(null) }
    val (state, reload) = useLoad(Unit, onAuthLost = onLogout) { api.get("worker_dashboard") }

    LaunchedEffect(state) {
        val worker = (state as? UiState.Ready<JSONObject>)?.value?.optJSONObject("worker")
        val fallback = worker?.let { it.str("photo_url").ifBlank { it.str("photo").ifBlank { it.str("profile_photo") } } }
        headerPhoto = try {
            val photo = api.file("worker_view_profile_photo", emptyMap(), "worker-profile.jpg")
            val mime = photo.mime.ifBlank { "image/jpeg" }
            "data:$mime;base64," + android.util.Base64.encodeToString(photo.bytes, android.util.Base64.NO_WRAP)
        } catch (_: Exception) { fallback?.takeIf { it.isNotBlank() && it != "null" } }
    }

    BackHandler(enabled = screen != null || tab != WTab.Home) {
        if (screen != null) screen = null else tab = WTab.Home
    }

    CompositionLocalProvider(LocalUserDisplayName provides name, LocalUserProfilePhoto provides headerPhoto) {
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
                    WScreen.Bank -> BankScreen { screen = null }
                    WScreen.Site -> Loaded(state, reload) { SiteTab(it, name) { screen = null } }
                    null -> when (tab) {
                        WTab.Home -> Loaded(state, reload) {
                            HomeTab(it, name, onSelectTab = { tab = it }, open = { screen = it })
                        }
                        WTab.Payslips -> PayslipsScreen()
                        WTab.Leave -> LeaveScreen { tab = WTab.Home }
                        WTab.ClockIn -> AttendanceScreen()
                        WTab.Profile -> Loaded(state, reload) {
                            ProfileTab(it, name, open = { screen = it }, onLogout = onLogout)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfilePhoto(worker: JSONObject, size: Int = 64) {
    val photo = worker.str("photo_url").ifBlank { worker.str("photo").ifBlank { worker.str("profile_photo") } }
    ProfileAvatar(worker.str("name"), size, photo)
}

@Composable
private fun QuickLinkTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().height(105.dp)
            .shadow(8.dp, shape).clip(shape)
            .background(Brush.linearGradient(listOf(Color.White, Color(0xFFF8F6FB))))
            .border(1.dp, Color.White, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(39.dp).shadow(7.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(tint, tint.copy(alpha = .78f)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(
            label, color = Color(0xFF303448), fontSize = 9.sp, lineHeight = 10.sp,
            fontWeight = FontWeight.ExtraBold, maxLines = 2, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun HomeTab(
    d: JSONObject,
    fallbackName: String,
    onSelectTab: (WTab) -> Unit,
    open: (WScreen) -> Unit
) {
    val w = d.optJSONObject("worker") ?: JSONObject()
    val ledger = d.optJSONObject("payments") ?: JSONObject()
    val years = ledger.keys().asSequence().sortedDescending().toList()
    val subs = d.optJSONArray("submissions").objects()
    val fullName = w.str("name").ifBlank { fallbackName }.trim().ifBlank { "Worker" }
    val firstName = fullName.substringBefore(" ").ifBlank { "Worker" }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 20.dp)
    ) {
        ScreenHeader("Hi, $firstName.", "Worker portal", profileName = fullName)

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(HeroBrush).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProfilePhoto(w, 58)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(fullName, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                Text("${w.str("role")} · ${w.str("site").ifBlank { "No site assigned" }}", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
                Text("Passport ${w.str("passport")}", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
            }
        }

        SectionTitle("Quick Links") {
            Text("Tap to open", color = Muted, fontSize = 11.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickLinkTile("Payslips", Icons.Filled.Receipt, Color(0xFF9E4CC0), Modifier.weight(1f)) { onSelectTab(WTab.Payslips) }
                QuickLinkTile("Bank Account", Icons.Filled.AccountBalance, Color(0xFF278FE2), Modifier.weight(1f)) { open(WScreen.Bank) }
                QuickLinkTile("Payment Proof", Icons.Filled.Payment, Color(0xFFE34D63), Modifier.weight(1f)) { open(WScreen.Payments) }
                QuickLinkTile("My Site", Icons.Filled.Home, Color(0xFFFF792F), Modifier.weight(1f)) { open(WScreen.Site) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickLinkTile("Leave", Icons.Filled.Event, Color(0xFFE8BD35), Modifier.weight(1f)) { onSelectTab(WTab.Leave) }
                QuickLinkTile("Clock-in", Icons.Filled.AccessTime, Color(0xFF7778D9), Modifier.weight(1f)) { onSelectTab(WTab.ClockIn) }
                QuickLinkTile("Payment History", Icons.Filled.History, Color(0xFF45B3E2), Modifier.weight(1f)) { open(WScreen.Payments) }
                QuickLinkTile("Profile", Icons.Filled.Person, Color(0xFF35B19A), Modifier.weight(1f)) { onSelectTab(WTab.Profile) }
            }
        }

        SectionTitle("Payment ledger")
        if (years.isEmpty()) SaymaCard { EmptyState("No payment record yet") }
        years.forEach { y ->
            val rec = ledger.optJSONObject(y) ?: return@forEach
            val target = rec.num("target")
            val paid = rec.num("paid")
            SaymaCard(Modifier.padding(bottom = 8.dp)) {
                Text(y, fontWeight = FontWeight.ExtraBold)
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("Target", rm(target), Modifier.weight(1f))
                    StatTile("Paid", rm(paid), Modifier.weight(1f), Good)
                    StatTile("Left", rm(Math.max(0.0, target - paid)), Modifier.weight(1f), Rose)
                }
                ProgressBar(if (target > 0) (paid / target).toFloat() else 0f)
                rec.optJSONArray("costItems").objects().forEach { ci ->
                    Row(Modifier.padding(top = 6.dp)) {
                        Text(ci.str("part"), Modifier.weight(1f), fontSize = 13.sp)
                        Text(rm(ci.num("amount")), fontSize = 13.sp)
                    }
                }
            }
        }

        SectionTitle("Recent submissions") {
            TextButton(onClick = { open(WScreen.Payments) }) { Text("View all", color = Orchid) }
        }
        SaymaCard {
            if (subs.isEmpty()) EmptyState("No submissions yet")
            subs.take(5).forEach { item ->
                ListRow(
                    rm(item.num("amount")),
                    item.str("payment_date") + if (item.str("note").isNotBlank()) " · " + item.str("note") else "",
                    item.str("status").replaceFirstChar { it.uppercase() },
                    ""
                )
            }
        }
    }
}

@Composable
private fun SiteTab(d: JSONObject, name: String, onBack: () -> Unit) {
    val worker = d.optJSONObject("worker") ?: JSONObject()
    val site = worker.str("site").ifBlank { d.str("site_name") }.ifBlank { "No site assigned" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        ScreenHeader("My Site", "Your assigned work location", profileName = name)
        SaymaCard {
            Text("CURRENT ASSIGNMENT", color = Orchid, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(9.dp))
            Text(site, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text(worker.str("role").ifBlank { "Worker" }, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            ListRow("Worker name", worker.str("name").ifBlank { name }, "", "")
            ListRow("Passport", worker.str("passport"), "", "")
            ListRow("Assigned site", site, "", "")
        }
        Spacer(Modifier.height(12.dp))
        SoftButton("Back", Modifier.fillMaxWidth(), onClick = onBack)
    }
}

@Composable
private fun ProfileTab(d: JSONObject, fallbackName: String, open: (WScreen) -> Unit, onLogout: () -> Unit) {
    val worker = d.optJSONObject("worker") ?: JSONObject()
    val fullName = worker.str("name").ifBlank { fallbackName }.ifBlank { "Worker" }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp).padding(bottom = 18.dp)
    ) {
        ScreenHeader("Profile", "Your account details", profileName = fullName)
        SaymaCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfilePhoto(worker, 62)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(fullName, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text(worker.str("role").ifBlank { "Worker" }, color = Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            ListRow("Assigned site", worker.str("site").ifBlank { "Not assigned" }, "", "")
            ListRow("Passport", worker.str("passport").ifBlank { "—" }, "", "")
            ListRow("Email / mobile", worker.str("email").ifBlank { worker.str("mobile").ifBlank { "—" } }, "", "")
        }
        SectionTitle("Account tools")
        SaymaCard {
            ListRow("Payment proof & history", "Submit receipts and track payments", "›", "", onClick = { open(WScreen.Payments) })
            ListRow("Bank account", "Apply for RHB / CIMB account", "›", "", onClick = { open(WScreen.Bank) })
        }
        Spacer(Modifier.height(14.dp))
        GradientButton("Sign out", Modifier.fillMaxWidth(), onClick = onLogout)
    }
}
