package com.sayma.builders.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.R
import kotlinx.coroutines.launch

private enum class LoginPortal { Choose, Admin, Worker }

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val api = apiOf()
    val scope = rememberCoroutineScope()
    var portal by remember { mutableStateOf(LoginPortal.Choose) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(true) }
    var showServer by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(api.baseUrl) }
    var registering by remember { mutableStateOf(false) }

    if (registering) {
        RegisterScreen(onBack = { registering = false })
        return
    }

    Box(Modifier.fillMaxSize().background(ScreenBrush)) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.sayma_logo), "SAYMA BUILDERS", Modifier.size(48.dp))
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("SAYMA BUILDERS", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    Text("CONSTRUCTION MANAGEMENT", color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                Text("● Secure", color = Good, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .78f)).padding(horizontal = 10.dp, vertical = 8.dp))
            }

            Spacer(Modifier.height(28.dp))

            if (portal == LoginPortal.Choose) {
                Text("WELCOME TO SAYMA BUILDERS", color = Orchid, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
                Spacer(Modifier.height(10.dp))
                Text("Choose your portal", color = Ink, fontSize = 29.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                Text("Secure access for company administrators and registered construction workers.",
                    color = Muted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                SaymaCard(Modifier.fillMaxWidth(), onClick = { portal = LoginPortal.Admin; msg = "" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFE9C9), Color(0xFFF8D8E8)))), contentAlignment = Alignment.Center) {
                            Text("▣", color = Color(0xFF9D5B87), fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Admin Portal", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Manage projects, workers, payments and company operations.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Administrator sign in   →", color = Orchid, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                SaymaCard(Modifier.fillMaxWidth(), onClick = { portal = LoginPortal.Worker; msg = "" }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(54.dp).clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFFDDF4EA), Color(0xFFDCEBFF)))), contentAlignment = Alignment.Center) {
                            Text("⚒", color = Teal, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Worker Portal", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                            Text("View your profile, assigned site and payment information.", color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                            Spacer(Modifier.height(8.dp))
                            Text("Worker sign in   →", color = Orchid, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { portal = LoginPortal.Choose; msg = ""; pass = "" }) {
                        Text("← Back to portal selection", color = Orchid, fontWeight = FontWeight.Bold)
                    }
                }
                SaymaCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(if (portal == LoginPortal.Admin) "ADMINISTRATOR ACCESS" else "WORKER ACCESS", color = Orchid, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                            Spacer(Modifier.height(4.dp))
                            Text(if (portal == LoginPortal.Admin) "Admin Portal" else "Worker Portal", color = Ink, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                            Text(if (portal == LoginPortal.Admin) "Use your existing SAYMA BUILDERS administrator account." else "Sign in using your registered email or mobile number.",
                                color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        Text(if (portal == LoginPortal.Admin) "Existing login" else "Worker account", color = Orchid, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF7EDF8)).padding(horizontal = 9.dp, vertical = 7.dp))
                    }
                    Spacer(Modifier.height(16.dp))
                    LabeledField(user, { user = it; msg = "" }, if (portal == LoginPortal.Admin) "Username" else "Email address or mobile number",
                        keyboard = if (portal == LoginPortal.Admin) KeyboardType.Text else KeyboardType.Email)
                    Spacer(Modifier.height(8.dp))
                    LabeledField(pass, { pass = it; msg = "" }, "Password", password = true)
                    if (msg.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(msg, color = if (isError) Bad else Good, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    GradientButton(if (busy) "Please wait…" else if (portal == LoginPortal.Admin) "Sign in to Admin Portal" else "Sign in to Worker Portal",
                        Modifier.fillMaxWidth(), enabled = !busy && user.isNotBlank() && pass.isNotBlank()) {
                        scope.launch {
                            busy = true
                            msg = ""
                            try {
                                api.baseUrl = server
                                if (portal == LoginPortal.Admin) api.post("login", mapOf("username" to user.trim(), "password" to pass))
                                else api.post("worker_login", mapOf("identifier" to user.trim(), "password" to pass))
                                api.rememberedRole = if (portal == LoginPortal.Admin) "admin" else "worker"
                                onLoggedIn()
                            } catch (e: Exception) {
                                isError = true
                                msg = e.message ?: "Sign in failed"
                            }
                            busy = false
                        }
                    }
                    if (portal == LoginPortal.Worker) {
                        TextButton(onClick = { registering = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                            Text("Create a worker account →", color = Orchid, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = { showServer = !showServer }) {
                Text(if (showServer) "Hide server settings" else "Server settings", color = Muted, fontSize = 12.sp)
            }
            if (showServer) {
                SaymaCard(Modifier.fillMaxWidth()) {
                    LabeledField(server, { server = it }, "Server address", keyboard = KeyboardType.Uri)
                }
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun RegisterScreen(onBack: () -> Unit) {
    val api = apiOf()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var passport by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Worker Registration", style = MaterialTheme.typography.titleLarge)
        Text("Admin অনুমোদন দিলে আপনি লগইন করতে পারবেন।", color = Muted, fontSize = 13.sp)
        LabeledField(name, { name = it }, "পুরো নাম")
        LabeledField(passport, { passport = it }, "Passport নম্বর")
        LabeledField(email, { email = it }, "Email", keyboard = KeyboardType.Email)
        LabeledField(mobile, { mobile = it }, "মোবাইল নম্বর", keyboard = KeyboardType.Phone)
        LabeledField(p1, { p1 = it }, "Password (কমপক্ষে ১০ অক্ষর)", password = true)
        LabeledField(p2, { p2 = it }, "Password আবার লিখুন", password = true)
        if (msg.isNotBlank()) Text(msg, color = if (ok) Good else Bad)
        GradientButton(if (busy) "অপেক্ষা করুন…" else "Submit registration", Modifier.fillMaxWidth(), enabled = !busy) {
            when {
                p1.length < 10 -> { ok = false; msg = "Password কমপক্ষে ১০ অক্ষরের হতে হবে।" }
                p1 != p2 -> { ok = false; msg = "Password দুটি মিলছে না।" }
                else -> scope.launch {
                    busy = true
                    try {
                        val r = api.post("worker_register", mapOf("name" to name.trim(), "passport" to passport.trim(), "email" to email.trim(), "mobile" to mobile.trim(), "password" to p1))
                        ok = true; msg = r.optString("message", "Registration submitted. Please wait for Admin approval.")
                    } catch (e: Exception) { ok = false; msg = e.message ?: "ব্যর্থ" }
                    busy = false
                }
            }
        }
        SoftButton("লগইনে ফিরে যান", Modifier.fillMaxWidth(), onClick = onBack)
    }
}

@Composable
fun SetPasswordScreen(onDone: () -> Unit, onLogout: () -> Unit) {
    val api = apiOf()
    val scope = rememberCoroutineScope()
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("নিজের Password তৈরি করুন", style = MaterialTheme.typography.titleLarge)
        Text("প্রথমবার প্রবেশের জন্য নতুন Password দিন (কমপক্ষে ১০ অক্ষর)।", color = Muted, fontSize = 13.sp)
        LabeledField(p1, { p1 = it }, "নতুন Password", password = true)
        LabeledField(p2, { p2 = it }, "Password আবার লিখুন", password = true)
        if (msg.isNotBlank()) Text(msg, color = Bad)
        GradientButton(if (busy) "অপেক্ষা করুন…" else "Save password & continue", Modifier.fillMaxWidth(), enabled = !busy) {
            when {
                p1.length < 10 -> msg = "কমপক্ষে ১০ অক্ষর ব্যবহার করুন।"
                p1 != p2 -> msg = "Password দুটি মিলছে না।"
                else -> scope.launch {
                    busy = true
                    try { api.post("worker_set_password", mapOf("password" to p1)); onDone() } catch (e: Exception) { msg = e.message ?: "ব্যর্থ" }
                    busy = false
                }
            }
        }
        SoftButton("Sign out", Modifier.fillMaxWidth(), onClick = onLogout)
    }
}
