package com.sayma.builders.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.R
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val api = apiOf()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(if (api.rememberedRole == "worker") 1 else 0) }
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

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.sayma_logo), "SAYMA", Modifier.size(104.dp))
        Spacer(Modifier.height(10.dp))
        Text("SAYMA BUILDERS", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Ink)
        Text("Secure Sign In", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))

        SaymaCard {
            TabRow(selectedTabIndex = tab, containerColor = CardBg, contentColor = Teal) {
                Tab(selected = tab == 0, onClick = { tab = 0; msg = "" }, text = { Text("Admin") })
                Tab(selected = tab == 1, onClick = { tab = 1; msg = "" }, text = { Text("Worker") })
            }
            Spacer(Modifier.height(14.dp))
            LabeledField(user, { user = it }, if (tab == 0) "Username" else "Email বা মোবাইল নম্বর")
            Spacer(Modifier.height(10.dp))
            LabeledField(pass, { pass = it }, "Password", password = true)
            if (msg.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(msg, color = if (isError) Bad else Good, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))
            GradientButton(if (busy) "অপেক্ষা করুন…" else "Sign In", Modifier.fillMaxWidth(), enabled = !busy && user.isNotBlank() && pass.isNotBlank()) {
                scope.launch {
                    busy = true; msg = ""
                    try {
                        api.baseUrl = server
                        if (tab == 0) api.post("login", mapOf("username" to user.trim(), "password" to pass))
                        else api.post("worker_login", mapOf("identifier" to user.trim(), "password" to pass))
                        api.rememberedRole = if (tab == 0) "admin" else "worker"
                        onLoggedIn()
                    } catch (e: Exception) { isError = true; msg = e.message ?: "সাইন ইন ব্যর্থ" }
                    busy = false
                }
            }
            if (tab == 1) {
                TextButton(onClick = { registering = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("নতুন Worker অ্যাকাউন্ট খুলুন", color = Teal) }
            }
        }

        Spacer(Modifier.height(10.dp))
        TextButton(onClick = { showServer = !showServer }) { Text(if (showServer) "Server লুকান" else "Server সেটিং", color = Muted, fontSize = 12.sp) }
        if (showServer) {
            LabeledField(server, { server = it }, "Server ঠিকানা (https://domain.com/portal/)", keyboard = KeyboardType.Uri)
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
