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

sealed interface Session {
    data object Checking : Session
    data class CheckFailed(val message: String) : Session
    data object LoggedOut : Session
    data class Admin(val username: String) : Session
    data class Worker(val name: String, val mustSetPassword: Boolean) : Session
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SaymaTheme { Surface(Modifier.fillMaxSize(), color = Bg) { Root() } } }
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
            val r = api.get("setup_status")
            when {
                !r.optBoolean("logged_in") -> Session.LoggedOut
                r.optString("role") == "admin" -> Session.Admin(r.optString("username", "Administrator"))
                else -> Session.Worker(r.optString("username", "Worker"), r.optBoolean("must_set_password"))
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
        is Session.Admin -> AdminApp(username = s.username, onLogout = { scope.launch { api.logout("admin"); session = Session.LoggedOut } })
        is Session.Worker ->
            if (s.mustSetPassword) SetPasswordScreen(onDone = { scope.launch { refresh() } }, onLogout = { scope.launch { api.logout("worker"); session = Session.LoggedOut } })
            else WorkerApp(name = s.name, onLogout = { scope.launch { api.logout("worker"); session = Session.LoggedOut } })
    }
}
