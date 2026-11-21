package com.sayma.builders.ui.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.sayma.builders.data.*
import com.sayma.builders.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import kotlin.coroutines.resume

@Composable
private fun Back(title: String, subtitle: String = "", onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Teal) }
        Column { Text(title, style = MaterialTheme.typography.titleLarge); if (subtitle.isNotBlank()) Text(subtitle, color = Muted) }
    }
}

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) =
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp), content = content)

/* ---------------- Payslips ---------------- */
@Composable
fun PayslipsScreen() {
    val api = apiOf()
    var err by remember { mutableStateOf("") }
    val (opener, host) = rememberFileOpener { err = it }
    val (state, reload) = useLoad(Unit) { api.get("worker_salary_slips").optJSONArray("slips").objects() }
    host()
    Page {
        ScreenHeader("Payslips", "Your monthly salary slips")
        if (err.isNotBlank()) Text(err, color = Bad)
        SaymaCard {
            Loaded(state, reload) { list ->
                if (list.isEmpty()) EmptyState("No payslip published yet")
                list.forEach { s ->
                    ListRow(s.str("pay_period"), "Gross ${rm(s.num("gross_amount"))} · Deductions ${rm(s.num("deductions_amount"))}", rm(s.num("net_amount")), "net pay") {
                        opener.open { api.file("worker_view_salary_slip", mapOf("id" to s.str("id")), s.str("original_filename").ifBlank { "payslip.pdf" }) }
                    }
                }
            }
        }
    }
}

/* ---------------- Payments ---------------- */
@Composable
fun PaymentsScreen(d: JSONObject, reload: Reload, onBack: () -> Unit) {
    val api = apiOf(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf<FilePart?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    val (opener, host) = rememberFileOpener { msg = it; ok = false }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) receipt = readUri(context, uri, "receipt") }
    val subs = d.optJSONArray("submissions").objects()
    host()
    Page {
        Back("Payments", "Payment confirmations", onBack)
        SaymaCard {
            Text("নতুন পেমেন্ট নিশ্চিতকরণ", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LabeledField(amount, { amount = it }, "Amount (RM)", keyboard = KeyboardType.Decimal)
            Spacer(Modifier.height(8.dp))
            LabeledField(date, { date = it }, "Payment date (YYYY-MM-DD)")
            Spacer(Modifier.height(8.dp))
            LabeledField(note, { note = it }, "Note", multiline = true)
            Spacer(Modifier.height(8.dp))
            SoftButton(receipt?.name ?: "Receipt সংযুক্ত করুন (PDF/ছবি)") { picker.launch("*/*") }
            if (msg.isNotBlank()) Text(msg, color = if (ok) Good else Bad, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            GradientButton(if (busy) "অপেক্ষা করুন…" else "Submit to Admin", Modifier.fillMaxWidth(), enabled = !busy && amount.isNotBlank()) {
                scope.launch {
                    busy = true
                    try {
                        val r = api.post("worker_submit_payment", mapOf("amount" to amount.trim(), "payment_date" to date.trim(), "note" to note.trim()), listOfNotNull(receipt))
                        ok = true; msg = r.optString("message", "Submitted."); amount = ""; note = ""; receipt = null; reload.trigger()
                    } catch (e: Exception) { ok = false; msg = e.message ?: "ব্যর্থ" }
                    busy = false
                }
            }
        }
        SectionTitle("History")
        SaymaCard {
            if (subs.isEmpty()) EmptyState("No submissions yet")
            subs.forEach { s ->
                Column(Modifier.padding(vertical = 8.dp)) {
                    Row { Text(rm(s.num("amount")), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Chip(s.str("status"), statusColor(s.str("status"))) }
                    Text(s.str("payment_date") + if (s.str("note").isNotBlank()) " · ${s.str("note")}" else "", color = Muted, fontSize = 12.sp)
                    if (s.str("admin_note").isNotBlank()) Text("Admin: ${s.str("admin_note")}", fontSize = 12.sp)
                    if (s.optBoolean("has_receipt")) TextButton(onClick = { opener.open { api.file("worker_view_payment_receipt", mapOf("id" to s.str("id")), "receipt.pdf") } }) { Text("Receipt দেখুন") }
                }
                HorizontalDivider(color = Line)
            }
        }
    }
}

/* ---------------- Leave ---------------- */
@Composable
fun LeaveScreen(onBack: () -> Unit) {
    val api = apiOf(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var end by remember { mutableStateOf(LocalDate.now().toString()) }
    var reason by remember { mutableStateOf("") }
    var mc by remember { mutableStateOf<FilePart?>(null) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    val (opener, host) = rememberFileOpener { msg = it; ok = false }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) mc = readUri(context, uri, "mc") }
    val (state, reload) = useLoad("leave") { api.get("worker_leave_requests").optJSONArray("requests").objects() }
    host()
    Page {
        Back("Leave requests", "Apply and track leave", onBack)
        SaymaCard {
            LabeledField(start, { start = it }, "Start date (YYYY-MM-DD)")
            Spacer(Modifier.height(8.dp))
            LabeledField(end, { end = it }, "End date (YYYY-MM-DD)")
            Spacer(Modifier.height(8.dp))
            LabeledField(reason, { reason = it }, "Reason", multiline = true)
            Spacer(Modifier.height(8.dp))
            SoftButton(mc?.name ?: "MC সংযুক্ত করুন (ঐচ্ছিক)") { picker.launch("*/*") }
            if (msg.isNotBlank()) Text(msg, color = if (ok) Good else Bad, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(10.dp))
            GradientButton(if (busy) "অপেক্ষা করুন…" else "Send leave request", Modifier.fillMaxWidth(), enabled = !busy && reason.isNotBlank()) {
                scope.launch {
                    busy = true
                    try {
                        val r = api.post("worker_submit_leave", mapOf("start_date" to start.trim(), "end_date" to end.trim(), "reason" to reason.trim()), listOfNotNull(mc))
                        ok = true; msg = r.optString("message", "Sent."); reason = ""; mc = null; reload.trigger()
                    } catch (e: Exception) { ok = false; msg = e.message ?: "ব্যর্থ" }
                    busy = false
                }
            }
        }
        SectionTitle("My requests")
        SaymaCard {
            Loaded(state, reload) { list ->
                if (list.isEmpty()) EmptyState("No leave requests")
                list.forEach { r ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row { Text("${r.str("start_date")} → ${r.str("end_date")}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Chip(r.str("status"), statusColor(r.str("status"))) }
                        Text(r.str("reason"), fontSize = 13.sp)
                        if (r.str("admin_note").isNotBlank()) Text("Admin: ${r.str("admin_note")}", color = Muted, fontSize = 12.sp)
                        if (r.optBoolean("has_mc")) TextButton(onClick = { opener.open { api.file("worker_view_leave_mc", mapOf("id" to r.str("id")), "mc.pdf") } }) { Text("MC দেখুন") }
                    }
                    HorizontalDivider(color = Line)
                }
            }
        }
    }
}

/* ---------------- Bank ---------------- */
@Composable
fun BankScreen(onBack: () -> Unit) {
    val api = apiOf(); val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    val (state, reload) = useLoad("bank") { api.get("worker_bank_account").optJSONArray("requests").objects() }
    fun apply(bank: String) = scope.launch {
        try { val r = api.post("worker_apply_bank_account", mapOf("bank_name" to bank)); ok = true; msg = r.optString("message"); reload.trigger() }
        catch (e: Exception) { ok = false; msg = e.message ?: "ব্যর্থ" }
    }
    Page {
        Back("Bank account", "Apply for RHB or CIMB", onBack)
        SaymaCard {
            Text("ব্যাংক বেছে নিন", fontWeight = FontWeight.Bold)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton("RHB", Modifier.weight(1f)) { apply("RHB") }
                GradientButton("CIMB", Modifier.weight(1f)) { apply("CIMB") }
            }
            if (msg.isNotBlank()) Text(msg, color = if (ok) Good else Bad, modifier = Modifier.padding(top = 6.dp))
        }
        SectionTitle("My requests")
        SaymaCard {
            Loaded(state, reload) { list ->
                if (list.isEmpty()) EmptyState("No bank requests")
                list.forEach { r ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row { Text(r.str("bank_name"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Chip(r.str("status"), statusColor(r.str("status"))) }
                        if (r.str("account_name").isNotBlank()) Text("Account name: ${r.str("account_name")}", fontSize = 13.sp)
                        if (r.str("account_number").isNotBlank()) Text("Account no: ${r.str("account_number")}", fontSize = 13.sp)
                        if (r.str("admin_note").isNotBlank()) Text("Admin: ${r.str("admin_note")}", color = Muted, fontSize = 12.sp)
                    }
                    HorizontalDivider(color = Line)
                }
            }
        }
    }
}

/* ---------------- Attendance (GPS + entry photo) ---------------- */
private fun hasLocationPermission(c: Context) =
    ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

@Suppress("MissingPermission", "DEPRECATION")
private suspend fun currentLocation(c: Context): Location? {
    val manager = c.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    // Do not rely on GPS alone: indoor GPS can be unavailable while network location still works.
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) {
        throw ApiException("ফোনের Location/GPS বন্ধ আছে। Quick Settings থেকে Location চালু করে আবার চেষ্টা করুন।")
    }

    val now = System.currentTimeMillis()
    var best: Location? = providers.mapNotNull { provider ->
        runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    }.filter { loc ->
        val age = now - loc.time
        age in 0..120_000L && loc.accuracy <= 100f
    }.minByOrNull { loc -> loc.accuracy + ((now - loc.time) / 10_000f) }

    if (best != null && best!!.accuracy <= 35f && now - best!!.time <= 30_000L) return best

    val startedAt = System.currentTimeMillis()
    val fresh = withTimeoutOrNull(28_000L) {
        suspendCancellableCoroutine<Location?> { cont ->
            val handler = Handler(Looper.getMainLooper())
            lateinit var listener: android.location.LocationListener
            lateinit var timeout: Runnable

            fun finish(location: Location?) {
                if (!cont.isActive) return
                runCatching { manager.removeUpdates(listener) }
                handler.removeCallbacks(timeout)
                cont.resume(location)
            }

            timeout = Runnable {
                val candidate = best?.takeIf {
                    val age = System.currentTimeMillis() - it.time
                    age in 0..120_000L && it.accuracy <= 100f
                }
                finish(candidate)
            }

            listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    val age = System.currentTimeMillis() - location.time
                    if (age in 0..120_000L && (best == null || location.accuracy < best!!.accuracy)) best = location
                    val candidate = best
                    when {
                        candidate == null -> Unit
                        candidate.accuracy <= 35f -> finish(candidate)
                        System.currentTimeMillis() - startedAt >= 10_000L && candidate.accuracy <= 85f -> finish(candidate)
                    }
                }
                override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            var registered = false
            providers.forEach { provider ->
                try {
                    manager.requestLocationUpdates(provider, 1_000L, 0f, listener, Looper.getMainLooper())
                    registered = true
                } catch (_: Exception) {}
            }
            if (!registered) finish(best) else handler.postDelayed(timeout, 26_000L)
            cont.invokeOnCancellation {
                handler.removeCallbacks(timeout)
                runCatching { manager.removeUpdates(listener) }
            }
        }
    }

    return fresh ?: best?.takeIf {
        val age = System.currentTimeMillis() - it.time
        age in 0..120_000L && it.accuracy <= 100f
    }
}

private suspend fun compressPhoto(file: File): ByteArray = withContext(Dispatchers.IO) {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, opts)
    var sample = 1
    while (opts.outWidth / sample > 1800 || opts.outHeight / sample > 1800) sample *= 2
    val bmp = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: throw ApiException("ছবি পড়া যায়নি, আবার তুলুন।")
    ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }.toByteArray()
}

@Composable
fun AttendanceScreen() {
    val api = apiOf(); val context = LocalContext.current; val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf("") }
    var ok by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var gpsSearching by remember { mutableStateOf(false) }
    var verified by remember { mutableStateOf<Location?>(null) }
    val (opener, host) = rememberFileOpener { msg = it; ok = false }
    val (state, reload) = useLoad("attendance") { api.get("worker_attendance") }
    val photoFile = remember { File(context.cacheDir, "captures").apply { mkdirs() }.let { File(it, "entry.jpg") } }

    fun fail(e: Exception) { ok = false; msg = e.message ?: "ব্যর্থ" }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        val loc = verified
        if (!taken || loc == null) { msg = "ছবি তোলা হয়নি।"; ok = false; return@rememberLauncherForActivityResult }
        scope.launch {
            busy = true
            try {
                val bytes = compressPhoto(photoFile)
                val r = api.post("worker_clock_in", mapOf("latitude" to loc.latitude.toString(), "longitude" to loc.longitude.toString(), "accuracy_m" to loc.accuracy.toString()),
                    listOf(FilePart("proof", "entry.jpg", "image/jpeg", bytes)))
                ok = true; msg = r.optString("message", "Clock-in recorded."); verified = null; reload.trigger()
            } catch (e: Exception) { fail(e) }
            busy = false
        }
    }

    fun verifyThenCapture() = scope.launch {
        busy = true; gpsSearching = true; msg = "GPS লোকেশন খোঁজা হচ্ছে… ফোনের Location চালু রাখুন।"
        try {
            val loc = currentLocation(context)
                ?: throw ApiException("লোকেশন পাওয়া যায়নি। প্রায় ২৫ সেকেন্ড চেষ্টা করা হয়েছে। Precise Location চালু রেখে খোলা জায়গায় আবার চেষ্টা করুন।")
            gpsSearching = false
            msg = "লোকেশন পাওয়া গেছে (${loc.accuracy.toInt()} মিটার accuracy)। সাইট যাচাই হচ্ছে…"
            val r = api.post("worker_attendance_check_location", mapOf("latitude" to loc.latitude.toString(), "longitude" to loc.longitude.toString(), "accuracy_m" to loc.accuracy.toString()))
            verified = loc; ok = true; msg = r.optString("message", "Location verified.")
            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", photoFile)
            camera.launch(uri)
        } catch (e: Exception) {
            gpsSearching = false
            fail(e)
        }
        busy = false
    }

    // Android 12+ requires coarse and fine location to be requested together.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) verifyThenCapture()
        else {
            ok = false
            msg = "Clock-in-এর জন্য Precise Location অনুমতি দিন। Approximate Location দিয়ে সাইট যাচাই করা যাবে না।"
        }
    }

    host()
    Page {
        ScreenHeader("Attendance", "Verified GPS entry & exit")
        Loaded(state, reload) { d ->
            val today = d.optJSONObject("today")
            val status = today?.str("status") ?: "not_started"
            SaymaCard {
                Text(d.str("site_name").ifBlank { "No site assigned" }, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text(if (d.optBoolean("location_configured")) "Geofence radius ${d.optInt("radius_m")} m" else "এই সাইটের লোকেশন এখনও সেট করা হয়নি", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                Chip(when (status) { "working" -> "Working"; "completed" -> "Completed"; else -> "Not started" }, statusColor(status))
                today?.let { Text("In ${it.str("clock_in_at")}  ·  Out ${it.str("clock_out_at").ifBlank { "—" }}", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                if (msg.isNotBlank()) Text(msg, color = if (ok) Good else if (gpsSearching) Teal else Bad, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                when (status) {
                    "not_started" -> GradientButton(
                        if (gpsSearching) "GPS লোকেশন খোঁজা হচ্ছে…" else if (busy) "অপেক্ষা করুন…" else "Clock in (লোকেশন + ছবি)",
                        Modifier.fillMaxWidth(), enabled = !busy
                    ) {
                        if (hasLocationPermission(context)) verifyThenCapture() else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                    "working" -> GradientButton(if (busy) "অপেক্ষা করুন…" else "Clock out", Modifier.fillMaxWidth(), enabled = !busy) {
                        scope.launch {
                            busy = true
                            try { val r = api.post("worker_clock_out"); ok = true; msg = r.optString("message", "Clock-out recorded."); reload.trigger() } catch (e: Exception) { fail(e) }
                            busy = false
                        }
                    }
                    else -> Text("আজকের হাজিরা সম্পন্ন।", color = Good, fontWeight = FontWeight.Bold)
                }
            }
            SectionTitle("Recent records")
            SaymaCard {
                val rec = d.optJSONArray("records").objects()
                if (rec.isEmpty()) EmptyState("No attendance records")
                rec.forEach { r ->
                    ListRow(r.str("date"), "${r.str("site_name")} · In ${r.str("clock_in_at").takeLast(8).take(5)} · Out ${r.str("clock_out_at").takeLast(8).take(5).ifBlank { "—" }}",
                        if (r.optBoolean("has_entry_proof")) "Photo" else "", "") {
                        if (r.optBoolean("has_entry_proof")) opener.open { api.file("worker_view_attendance_proof", mapOf("id" to r.str("id")), "entry-photo.jpg") }
                    }
                }
            }
        }
    }
}
