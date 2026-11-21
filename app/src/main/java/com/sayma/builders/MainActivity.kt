package com.sayma.builders

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sayma.builders.data.ApiException
import com.sayma.builders.ui.*
import com.sayma.builders.ui.admin.AdminApp
import com.sayma.builders.ui.worker.WorkerApp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

sealed interface Session {
    data object Checking : Session
    data class CheckFailed(val message: String) : Session
    data object LoggedOut : Session
    data class Admin(val username: String, val displayName: String, val profilePhoto: String?) : Session
    data class Worker(val name: String, val mustSetPassword: Boolean) : Session
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SaymaTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) { delay(1700); showSplash = false }
                Surface(Modifier.fillMaxSize(), color = Bg) {
                    if (showSplash) CyberSplashScreen() else Root()
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val api = apiOf()
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<Session>(Session.Checking) }

    suspend fun refresh() {
        session = Session.Checking
        session = try {
            // Admin and Worker use separate PHP sessions/cookies on the existing API.
            val adminStatus = api.get("setup_status")
            if (adminStatus.optBoolean("logged_in")) {
                val username = adminStatus.optString("username", "Administrator")
                val profile = try { api.adminProfile().optJSONObject("profile") } catch (_: Exception) { null }
                val displayName = profile?.optString("name")?.trim().orEmpty()
                val photo = profile?.let { p ->
                    listOf("photo", "photo_url", "profile_photo", "avatar").firstNotNullOfOrNull { key ->
                        p.optString(key).trim().takeIf { it.isNotBlank() && it != "null" }
                    }
                }
                Session.Admin(username, displayName.ifBlank { friendlyAdminName(username) }, photo)
            } else {
                val workerStatus = try {
                    api.get("worker_session")
                } catch (e: ApiException) {
                    if (e.authRequired) null else throw e
                }
                if (workerStatus == null) {
                    Session.LoggedOut
                } else {
                    val worker = workerStatus.optJSONObject("worker")
                    Session.Worker(
                        worker?.optString("name")?.takeIf { it.isNotBlank() } ?: "Worker",
                        workerStatus.optBoolean("must_set_password")
                    )
                }
            }
        } catch (e: ApiException) { Session.CheckFailed(e.message ?: "সংযোগ ব্যর্থ") }
    }

    LaunchedEffect(Unit) { refresh() }

    when (val s = session) {
        Session.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Teal)
                Spacer(Modifier.height(12.dp))
                Text("নিরাপদ সেশন যাচাই হচ্ছে…", color = Muted)
            }
        }
        is Session.CheckFailed -> Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("সেশন যাচাই করা যায়নি", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(s.message, color = Bad)
            Spacer(Modifier.height(16.dp))
            GradientButton("আবার চেষ্টা করুন") { scope.launch { refresh() } }
            Spacer(Modifier.height(8.dp))
            SoftButton("লগইন স্ক্রিনে যান") { session = Session.LoggedOut }
        }
        Session.LoggedOut -> LoginScreen(onLoggedIn = { scope.launch { refresh() } })
        is Session.Admin -> AdminApp(username = s.username, displayName = s.displayName, profilePhoto = s.profilePhoto, onLogout = { scope.launch { api.logout("admin"); session = Session.LoggedOut } })
        is Session.Worker ->
            if (s.mustSetPassword) SetPasswordScreen(onDone = { scope.launch { refresh() } }, onLogout = { scope.launch { api.logout("worker"); session = Session.LoggedOut } })
            else WorkerApp(name = s.name, onLogout = { scope.launch { api.logout("worker"); session = Session.LoggedOut } })
    }
}

private fun friendlyAdminName(username: String): String {
    val cleaned = username.replace(Regex("\\d+$"), "").replace(Regex("[._-]+"), " ").trim()
    if (cleaned.isBlank()) return "Admin"
    return cleaned.split(Regex("\\s+")).joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercase() } }
}
