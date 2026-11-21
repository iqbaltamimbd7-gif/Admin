package com.sayma.builders.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.R
import com.sayma.builders.data.ApiException
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val api = apiOf()
    val scope = rememberCoroutineScope()
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(true) }
    var showServer by remember { mutableStateOf(false) }
    var server by remember { mutableStateOf(api.baseUrl) }
    var registering by remember { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }

    if (registering) {
        RegisterScreen(onBack = { registering = false })
        return
    }

    Box(Modifier.fillMaxSize().background(Color(0xFFDCDDEB))) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Box(Modifier.fillMaxWidth().height(286.dp)) {
                LoginIllustration(Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.TopStart).padding(start = 25.dp, top = 26.dp)) {
                    Text("HELLO", color = Color(0xFF111642), fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-.8).sp)
                    Text("Welcome back to your workspace", color = Color(0xFF27345F), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(7.dp))
                    Text("CYBER BUILDERS PORTAL", color = Color(0xFF315A8B), fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                }
                Text("SAYMA BUILDERS", color = Color(0xFF24416C), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = 22.dp, bottom = 27.dp))
            }

            Column(
                Modifier.fillMaxWidth().offset(y = (-22).dp)
                    .shadow(18.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Sign In", color = Color(0xFF17234D), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("Enter your company account details to continue", color = Color(0xFF9298A8), fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(19.dp))
                LoginField(identifier, { identifier = it; message = "" }, "Email / username / mobile", keyboard = KeyboardType.Email)
                Spacer(Modifier.height(9.dp))
                LoginField(password, { password = it; message = "" }, "Password", password = !showPassword, trailing = {
                    TextButton(onClick = { showPassword = !showPassword }, contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp)) {
                        Text(if (showPassword) "Hide" else "Show", color = Color(0xFF6F7895), fontSize = 10.sp)
                    }
                })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { isError = true; message = "Please contact your administrator to reset your password." }, contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)) {
                        Text("Forgot password?", color = Color(0xFF27345F), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                    }
                }
                if (message.isNotBlank()) {
                    Text(message, color = if (isError) Color(0xFFCA4655) else Color(0xFF2E9E6B), fontSize = 11.sp, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = ""
                            try {
                                api.baseUrl = server
                                // Admin currently authenticates with its existing username; Worker uses email/mobile.
                                try {
                                    api.post("login", mapOf("username" to identifier.trim(), "password" to password))
                                    api.rememberedRole = "admin"
                                    onLoggedIn()
                                } catch (adminError: ApiException) {
                                    if (!adminError.authRequired) throw adminError
                                    try {
                                        api.post("worker_login", mapOf("identifier" to identifier.trim(), "password" to password))
                                        api.rememberedRole = "worker"
                                        onLoggedIn()
                                    } catch (workerError: Exception) {
                                        throw workerError
                                    }
                                }
                            } catch (e: Exception) {
                                isError = true
                                message = e.message ?: "Sign in failed. Check your account details."
                            }
                            busy = false
                        }
                    },
                    enabled = !busy && identifier.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF202F66), disabledContainerColor = Color(0xFF8992B0))
                ) { Text(if (busy) "Signing in…" else "Sign In", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }

                Spacer(Modifier.height(13.dp))
                Text("OR CONTINUE WITH", color = Color(0xFF9AA0AF), fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    SocialPlaceholder("G", "Google", Color(0xFF4285F4), Modifier.weight(1f))
                    SocialPlaceholder("f", "Facebook", Color(0xFF1877F2), Modifier.weight(1f))
                }
                Spacer(Modifier.height(9.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Don't have an account?", color = Color(0xFF9AA0AF), fontSize = 10.sp)
                    TextButton(onClick = { registering = true }, contentPadding = PaddingValues(start = 5.dp, end = 0.dp, top = 0.dp, bottom = 0.dp)) {
                        Text("Sign Up", color = Color(0xFF202F66), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Worker: email/mobile  ·  Admin: existing username", color = Color(0xFFA5AAB6), fontSize = 9.sp, textAlign = TextAlign.Center)
                TextButton(onClick = { showServer = !showServer }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (showServer) "Hide server settings" else "Server settings", color = Color(0xFF8D94A6), fontSize = 9.sp)
                }
                if (showServer) {
                    LoginField(server, { server = it }, "Server address", keyboard = KeyboardType.Uri)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun LoginField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth().height(54.dp),
        singleLine = true, placeholder = { Text(placeholder, color = Color(0xFF9DA3B0), fontSize = 11.sp) },
        shape = CircleShape, trailingIcon = trailing,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFFF0F1F6), unfocusedContainerColor = Color(0xFFF0F1F6),
            focusedBorderColor = Color(0xFF2B3A70), unfocusedBorderColor = Color.Transparent,
            focusedTextColor = Color(0xFF1D294F), unfocusedTextColor = Color(0xFF1D294F), cursorColor = Color(0xFF2B3A70)
        )
    )
}

@Composable
private fun SocialPlaceholder(letter: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(42.dp), shape = RoundedCornerShape(12.dp),
        color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8EAF0))
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(letter, color = accent, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.width(7.dp))
            Text(label, color = Color(0xFF59627D), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LoginIllustration(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFFE8F6FF), Color(0xFFC9E7FA), Color(0xFFA8D7F4))), size = size)
        fun cloud(x: Float, y: Float, scale: Float) {
            drawCircle(Color.White.copy(alpha = .96f), 17f * scale, Offset(x, y))
            drawCircle(Color.White.copy(alpha = .96f), 22f * scale, Offset(x + 20f * scale, y - 7f * scale))
            drawCircle(Color.White.copy(alpha = .96f), 15f * scale, Offset(x + 43f * scale, y + 1f * scale))
            drawRoundRect(Color.White.copy(alpha = .96f), Offset(x - 4f * scale, y), Size(52f * scale, 15f * scale), CornerRadius(8f * scale))
        }
        cloud(w * .70f, h * .19f, .72f)
        cloud(w * .90f, h * .39f, .48f)
        val base = h * .88f
        val buildings = listOf(.25f to .31f, .34f to .44f, .43f to .56f, .53f to .40f, .62f to .62f, .72f to .49f, .82f to .68f, .91f to .42f)
        buildings.forEachIndexed { index, item ->
            val x = w * item.first
            val bw = w * (.075f + (index % 3) * .012f)
            val bh = h * item.second
            val top = base - bh
            drawRoundRect(
                Brush.linearGradient(listOf(Color(0xFF8DD5F5), Color(0xFF2D9DD8), Color(0xFF63B9E7))),
                Offset(x, top), Size(bw, bh), CornerRadius(5f)
            )
            drawLine(Color.White.copy(alpha = .55f), Offset(x + bw * .23f, top + 5f), Offset(x + bw * .23f, base - 4f), 1.2f)
            for (row in 0..3) for (col in 0..1) {
                val wx = x + bw * (.48f + col * .25f)
                val wy = top + 13f + row * 18f
                if (wy < base - 6f) drawRoundRect(Color.White.copy(alpha = .52f), Offset(wx, wy), Size(4f, 8f), CornerRadius(1.5f))
            }
        }
        drawRect(Color(0xFF79C2E9), Offset(0f, h * .88f), Size(w, h * .12f))
        // A small illustrated car anchors the skyline, like the supplied reference.
        val carX = w * .17f
        val carY = h * .82f
        drawRoundRect(Color(0xFFFAFCFF), Offset(carX, carY), Size(w * .23f, h * .065f), CornerRadius(12f))
        drawRoundRect(Color(0xFF7EBBE8), Offset(carX + w * .055f, carY - h * .045f), Size(w * .12f, h * .05f), CornerRadius(9f))
        drawCircle(Color(0xFF1E2B50), 10f, Offset(carX + w * .055f, carY + h * .065f))
        drawCircle(Color(0xFF1E2B50), 10f, Offset(carX + w * .18f, carY + h * .065f))
        drawCircle(Color(0xFFCBD6E8), 4f, Offset(carX + w * .055f, carY + h * .065f))
        drawCircle(Color(0xFFCBD6E8), 4f, Offset(carX + w * .18f, carY + h * .065f))
        // Soft plant silhouette on the left edge.
        drawLine(Color(0xFF4E91C9), Offset(w * .07f, h * .91f), Offset(w * .08f, h * .70f), 3f)
        drawOval(Color(0xFF7BA8EC), Offset(w * .01f, h * .72f), Size(w * .09f, h * .035f))
        drawOval(Color(0xFF8AB9F0), Offset(w * .04f, h * .68f), Size(w * .09f, h * .035f))
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
