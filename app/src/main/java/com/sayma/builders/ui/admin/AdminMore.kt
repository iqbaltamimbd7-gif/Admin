package com.sayma.builders.ui.admin

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.data.*
import com.sayma.builders.ui.*
import kotlinx.coroutines.launch

@Composable
fun MoreScreen(username: String, open: (AdminScreen) -> Unit, onLogout: () -> Unit, reload: Reload) {
    val items = listOf(
        Triple("Documents", "Company documents, versions & expiry", AdminScreen.Documents as AdminScreen),
        Triple("Attendance", "Verified GPS entry/exit and leave requests", AdminScreen.Attendance),
        Triple("Approval inbox", "Registrations, payments, bank & leave requests", AdminScreen.Requests),
        Triple("Worker salary slips", "Published monthly payslips", AdminScreen.SalarySlips),
        Triple("Documentation tools", "Saved links & utilities", AdminScreen.Tools),
        Triple("Expiry reminders", "Permit and CIDB due dates", AdminScreen.Reminders)
    )
    ScreenScroll {
        ScreenHeader("More", "Signed in as $username")
        SaymaCard { items.forEach { (t, d, s) -> ListRow(t, d, "›", "", onClick = { open(s) }) } }
        Spacer(Modifier.height(12.dp))
        SoftButton("Refresh data", Modifier.fillMaxWidth(), onClick = reload.trigger)
        Spacer(Modifier.height(8.dp))
        GradientButton("Sign out", Modifier.fillMaxWidth(), onClick = onLogout)
    }
}

@Composable
fun DocumentsScreen(onBack: () -> Unit) {
    val api = apiOf()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("ALL") }
    var editing by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var deleteTarget by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Other") }
    var issueDate by remember { mutableStateOf("") }
    var expiryDate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var pickedPart by remember { mutableStateOf<FilePart?>(null) }
    var saving by remember { mutableStateOf(false) }
    val (opener, host) = rememberFileOpener { err = it }
    val (state, reload) = useLoad(Unit) {
        val docs = api.get("list_documents").optJSONArray("documents").objects()
        val cats = try {
            api.get("list_document_categories").let { it.optJSONArray("categories") ?: it.optJSONArray("items") }.let { arr ->
                (0 until (arr?.length() ?: 0)).map { i ->
                    arr!!.opt(i).let { c -> if (c is org.json.JSONObject) c.str("key").ifBlank { c.str("label") } else c.toString() }
                }
            }
        } catch (_: ApiException) { emptyList() }
        docs to cats
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pickedPart = readUri(context, uri, "file")
    }
    host()

    fun openNew(cats: List<String>) {
        editing = null; title = ""; category = cats.firstOrNull() ?: "Other"
        issueDate = ""; expiryDate = ""; notes = ""; pickedPart = null; err = ""; showForm = true
    }
    fun openEdit(d: org.json.JSONObject) {
        editing = d; title = d.str("title"); category = d.str("category").ifBlank { "Other" }
        issueDate = d.str("issue_date"); expiryDate = d.str("expiry_date"); notes = d.str("notes")
        pickedPart = null; err = ""; showForm = true
    }

    ScreenScroll {
        BackBar("Documents", "Company documents", onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton("+ Upload document", Modifier.weight(1f)) {
                val cats = (state as? UiState.Ready)?.value?.second ?: emptyList()
                openNew(cats)
            }
            SoftButton("↻ Refresh", onClick = reload.trigger)
        }
        if (err.isNotBlank() && !showForm && deleteTarget == null) Text(err, color = Bad)
        Loaded(state, reload) { (docs, cats) ->
            val allCats = (listOf("ALL") + cats.ifEmpty { docs.map { it.str("category") }.distinct() }).distinct()
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                allCats.forEach { c -> FilterChip(selected = filter == c, onClick = { filter = c }, label = { Text(c) }) }
            }
            Spacer(Modifier.height(8.dp))
            val shown = docs.filter { filter == "ALL" || it.str("category").equals(filter, true) }
            SaymaCard {
                if (shown.isEmpty()) EmptyState("No documents")
                shown.forEach { d ->
                    val exp = d.str("expiry_date")
                    Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                        Text(d.str("title").ifBlank { d.str("original_name") }, fontWeight = FontWeight.Bold)
                        Text("${d.str("category")} · v${d.str("version")}" + if (exp.isNotBlank()) " · expires $exp" else "", color = Muted, fontSize = 12.sp)
                        Text(d.str("original_name"), color = Muted, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { opener.open { api.file("view_document", mapOf("id" to d.str("id")), d.str("original_name")) } }) { Text("Open") }
                            TextButton(onClick = { openEdit(d) }) { Text("Edit", color = Orchid) }
                            TextButton(onClick = { deleteTarget = d }) { Text("Delete", color = Bad) }
                        }
                        HorizontalDivider(color = Line)
                    }
                }
            }
        }
    }

    if (showForm) {
        val cats = (state as? UiState.Ready)?.value?.second ?: emptyList()
        AlertDialog(
            onDismissRequest = { if (!saving) showForm = false },
            title = { Text(if (editing == null) "Upload company document" else "Update company document") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LabeledField(title, { title = it }, "Document title")
                    Text("Category", color = Muted, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (cats.ifEmpty { listOf("Other") }).forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) })
                        }
                    }
                    LabeledField(issueDate, { issueDate = it }, "Issue date (YYYY-MM-DD)")
                    LabeledField(expiryDate, { expiryDate = it }, "Expiry date (YYYY-MM-DD)")
                    LabeledField(notes, { notes = it }, "Notes")
                    SoftButton(
                        if (pickedPart != null) "Selected: ${pickedPart!!.name}" else if (editing != null) "Choose replacement file (optional)" else "Choose file (required)",
                        Modifier.fillMaxWidth()
                    ) { picker.launch(arrayOf("*/*")) }
                    Text("PDF, image or Office file · maximum 15 MB. Replacing a file uploads a new version.", color = Muted, fontSize = 11.sp)
                    if (err.isNotBlank()) Text(err, color = Bad, fontSize = 12.sp)
                }
            },
            confirmButton = {
                GradientButton(if (saving) "Saving…" else "Save document", enabled = !saving) {
                    if (title.isBlank()) { err = "Document title is required."; return@GradientButton }
                    if (editing == null && pickedPart == null) { err = "Choose a document file."; return@GradientButton }
                    scope.launch {
                        saving = true; err = ""
                        try {
                            val fields = mapOf("title" to title.trim(), "category" to category, "issue_date" to issueDate.trim(), "expiry_date" to expiryDate.trim(), "notes" to notes.trim())
                            val part = pickedPart
                            if (part != null) {
                                if (part.bytes.size > 15 * 1024 * 1024) throw ApiException("File exceeds 15 MB limit.")
                                api.post("upload_document", fields, listOf(part))
                            } else {
                                val doc = editing ?: throw ApiException("Choose a file.")
                                api.post("update_document", fields + ("id" to doc.str("id")))
                            }
                            showForm = false; editing = null; pickedPart = null; reload.trigger()
                        } catch (e: Exception) { err = e.message ?: "Document save failed." }
                        saving = false
                    }
                }
            },
            dismissButton = { TextButton(onClick = { if (!saving) showForm = false }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete document?") },
            text = { Text("'${doc.str("title").ifBlank { doc.str("original_name") }}' will be removed from the company document register.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            api.post("delete_document", mapOf("id" to doc.str("id")))
                            deleteTarget = null; reload.trigger()
                        } catch (e: Exception) { err = e.message ?: "Delete failed."; deleteTarget = null }
                    }
                }) { Text("Delete", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
@Composable
fun AttendanceScreen(onBack: () -> Unit) {
    val api = apiOf()
    var err by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    val (opener, host) = rememberFileOpener { err = it }
    val scope = rememberCoroutineScope()
    val (locs, reloadLocs) = useLoad("locs") { api.get("admin_list_worker_attendance_locations").optJSONArray("locations").objects() }
    val (recs, reloadRecs) = useLoad(date) { api.get("admin_list_worker_attendance", mapOf("date" to date)).optJSONArray("records").objects() }
    val (leaves, reloadLeaves) = useLoad("leaves") { api.get("admin_list_worker_leave_requests").optJSONArray("requests").objects() }
    host()
    ScreenScroll {
        BackBar("Attendance", "Site locations, GPS entry/exit & leave", onBack)
        TabRow(selectedTabIndex = tab, containerColor = CardBg, contentColor = Teal) {
            listOf("Records", "Leave", "Sites").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Spacer(Modifier.height(10.dp))
        if (err.isNotBlank()) Text(err, color = Bad)
        when (tab) {
            0 -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SoftButton("◀", onClick = { date = java.time.LocalDate.parse(date).minusDays(1).toString() })
                    Text(date, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                    SoftButton("▶", onClick = { date = java.time.LocalDate.parse(date).plusDays(1).toString() })
                }
                Spacer(Modifier.height(8.dp))
                SaymaCard {
                    Loaded(recs, reloadRecs) { list ->
                        if (list.isEmpty()) EmptyState("এই তারিখে কোনো রেকর্ড নেই")
                        list.forEach { r ->
                            val inT = r.str("clock_in_at").takeLast(8).take(5); val outT = r.str("clock_out_at").takeLast(8).take(5)
                            ListRow(r.str("worker_name"), "${r.str("site_name")} · In ${inT.ifBlank { "—" }} · Out ${outT.ifBlank { "—" }}",
                                if (r.optBoolean("has_entry_proof")) "Photo" else "", "") {
                                if (r.optBoolean("has_entry_proof")) opener.open { api.file("admin_view_worker_attendance_proof", mapOf("id" to r.str("id")), "entry-photo.jpg") }
                            }
                        }
                    }
                }
            }
            1 -> SaymaCard {
                Loaded(leaves, reloadLeaves) { list ->
                    if (list.isEmpty()) EmptyState("No leave requests")
                    list.forEach { r ->
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Row { Text(r.str("worker_name"), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Chip(r.str("status"), statusColor(r.str("status"))) }
                            Text("${r.str("start_date")} → ${r.str("end_date")}", color = Muted, fontSize = 12.sp)
                            Text(r.str("reason"), fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (r.optBoolean("has_mc")) SoftButton("MC দেখুন") { opener.open { api.file("admin_view_worker_leave_mc", mapOf("id" to r.str("id")), "mc.pdf") } }
                                if (r.str("status") == "pending") {
                                    GradientButton("Approve") { scope.launch { try { api.post("admin_review_worker_leave_request", mapOf("request_id" to r.str("id"), "decision" to "approve")); reloadLeaves.trigger() } catch (e: Exception) { err = e.message ?: "" } } }
                                    SoftButton("Reject") { scope.launch { try { api.post("admin_review_worker_leave_request", mapOf("request_id" to r.str("id"), "decision" to "reject")); reloadLeaves.trigger() } catch (e: Exception) { err = e.message ?: "" } } }
                                }
                            }
                        }
                        HorizontalDivider(color = Line)
                    }
                }
            }
            else -> SaymaCard {
                Loaded(locs, reloadLocs) { list ->
                    if (list.isEmpty()) EmptyState("No site locations")
                    list.forEach { l -> ListRow(l.str("site_name"), "Radius ${l.str("radius_m")} m · ${l.str("latitude")}, ${l.str("longitude")}", if (l.optBoolean("enabled")) "Enabled" else "Off", "") }
                }
            }
        }
    }
}

@Composable
fun RequestsScreen(onBack: () -> Unit) {
    val api = apiOf()
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf("") }
    val (opener, host) = rememberFileOpener { err = it }
    val (state, reload) = useLoad(Unit) { api.get("admin_list_worker_portal_requests") }
    fun review(action: String, idField: String, id: String, decision: String) = scope.launch {
        try { api.post(action, mapOf(idField to id, "decision" to decision)); reload.trigger() } catch (e: Exception) { err = e.message ?: "" }
    }
    host()
    ScreenScroll {
        BackBar("Approval inbox", "Worker requests waiting for review", onBack)
        if (err.isNotBlank()) Text(err, color = Bad)
        Loaded(state, reload) { r ->
            val accounts = r.optJSONArray("accounts").objects(); val pays = r.optJSONArray("payments").objects()
            val banks = r.optJSONArray("bank_requests").objects(); val leaves = r.optJSONArray("leave_requests").objects()
            if (accounts.isEmpty() && pays.isEmpty() && banks.isEmpty() && leaves.isEmpty()) EmptyState("কোনো অপেক্ষমাণ অনুরোধ নেই")
            if (accounts.isNotEmpty()) { SectionTitle("Worker registrations"); SaymaCard { accounts.forEach { a ->
                Text(a.str("worker_name"), fontWeight = FontWeight.Bold); Text("${a.str("passport_no")} · ${a.str("mobile")} · ${a.str("email")}", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    GradientButton("Approve") { review("admin_review_worker_account", "account_id", a.str("id"), "approve") }
                    SoftButton("Reject") { review("admin_review_worker_account", "account_id", a.str("id"), "reject") }
                }; HorizontalDivider(color = Line) } } }
            if (pays.isNotEmpty()) { SectionTitle("Payment confirmations"); SaymaCard { pays.forEach { p ->
                Text("${p.str("worker_name")} · ${rm(p.num("amount"))}", fontWeight = FontWeight.Bold); Text("${p.str("payment_date")} · ${p.str("note")}", color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    if (p.optBoolean("has_receipt")) SoftButton("Receipt") { opener.open { api.file("admin_view_worker_payment_receipt", mapOf("id" to p.str("id")), "receipt.pdf") } }
                    GradientButton("Approve") { review("admin_review_worker_payment", "submission_id", p.str("id"), "approve") }
                    SoftButton("Reject") { review("admin_review_worker_payment", "submission_id", p.str("id"), "reject") }
                }; HorizontalDivider(color = Line) } } }
            if (banks.isNotEmpty()) { SectionTitle("Bank account requests"); SaymaCard { banks.forEach { b ->
                Text("${b.str("worker_name")} · ${b.str("bank_name")}", fontWeight = FontWeight.Bold); Text(b.str("passport_no"), color = Muted, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    GradientButton("Approve") { review("admin_review_worker_bank_request", "request_id", b.str("id"), "approve") }
                    SoftButton("Reject") { review("admin_review_worker_bank_request", "request_id", b.str("id"), "reject") }
                }; HorizontalDivider(color = Line) } } }
            if (leaves.isNotEmpty()) { SectionTitle("Leave requests"); SaymaCard { leaves.forEach { l ->
                ListRow(l.str("worker_name"), "${l.str("start_date")} → ${l.str("end_date")} · ${l.str("reason")}", "", "") } }
                Text("Leave অনুমোদন করতে Attendance → Leave ট্যাব ব্যবহার করুন।", color = Muted, fontSize = 12.sp) }
        }
    }
}

@Composable
fun SalarySlipsScreen(onBack: () -> Unit) {
    val api = apiOf()
    var err by remember { mutableStateOf("") }
    val (opener, host) = rememberFileOpener { err = it }
    val (state, reload) = useLoad(Unit) { api.get("admin_list_worker_salary_slips").optJSONArray("slips").objects() }
    host()
    ScreenScroll {
        BackBar("Worker salary slips", "Published monthly payslips", onBack)
        if (err.isNotBlank()) Text(err, color = Bad)
        SaymaCard {
            Loaded(state, reload) { list ->
                if (list.isEmpty()) EmptyState("No salary slips uploaded")
                list.forEach { s ->
                    ListRow(s.str("worker_name"), "${s.str("pay_period")} · ${s.str("original_filename")}", rm(s.num("net_amount")), "net") {
                        opener.open { api.file("admin_view_worker_salary_slip", mapOf("id" to s.str("id")), s.str("original_filename")) }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsScreen(onBack: () -> Unit) {
    val api = apiOf()
    val context = LocalContext.current
    val (state, reload) = useLoad(Unit) { api.get("list_documentation_tools").optJSONArray("items").objects() }
    ScreenScroll {
        BackBar("Documentation tools", "Saved links & utilities", onBack)
        SaymaCard {
            Loaded(state, reload) { list ->
                if (list.isEmpty()) EmptyState("No tools saved")
                list.forEach { t ->
                    ListRow(t.str("name"), t.str("description").ifBlank { t.str("url") }, "Open", "") {
                        // Third-party sites are opened in the phone's browser, not inside a WebView.
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(t.str("url"))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                    }
                }
            }
        }
    }
}
