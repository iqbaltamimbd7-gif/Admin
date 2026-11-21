package com.sayma.builders.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.data.*
import com.sayma.builders.ui.*
import kotlinx.coroutines.launch

private fun workerOptionKey(worker: WorkerRec, index: Int): String {
    val identity = listOf(worker.raw.str("id"), worker.raw.str("worker_id"), worker.passport, worker.email, worker.mobile, worker.name)
        .firstOrNull { it.isNotBlank() }.orEmpty()
    return "$identity#$index"
}

@Composable
fun WorkersScreen(data: AdminData, onOpen: (WorkerRec) -> Unit) {
    var q by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val options = data.workers.mapIndexed { index, worker -> worker to workerOptionKey(worker, index) }
    var selectedOptionKey by remember(options.map { it.second }) { mutableStateOf(options.firstOrNull()?.second.orEmpty()) }
    val filtered = options.filter { (worker, _) ->
        "${worker.name} ${worker.role} ${worker.passport} ${worker.site} ${worker.email} ${worker.mobile}".contains(q, true)
    }
    val selected = options.firstOrNull { it.second == selectedOptionKey }?.first ?: options.firstOrNull()?.first

    ScreenScroll {
        ScreenHeader("Workers", "${data.workers.size} workforce profiles")
        SearchPill(q, { q = it }, "Search worker, role, passport or email…")
        SectionTitle("Select a worker") { Text("Showing ${filtered.size} of ${data.workers.size}", color = Muted, fontSize = 10.sp) }
        Box(Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.Start) {
                    Text(selected?.name ?: "Choose a worker", fontWeight = FontWeight.Bold, maxLines = 1)
                    if (selected != null) Text("${selected.role} · ${selected.passport.ifBlank { selected.email.ifBlank { "No ID" } }}", color = Muted, fontSize = 11.sp, maxLines = 1)
                }
                Text("▾", color = Teal, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 420.dp)) {
                filtered.forEach { (worker, optionKey) ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(worker.name.ifBlank { "Unnamed worker" }, fontWeight = FontWeight.Bold)
                                Text(listOf(worker.role, worker.passport, worker.email).filter { it.isNotBlank() }.joinToString(" · "), color = Muted, fontSize = 11.sp, maxLines = 1)
                            }
                        },
                        onClick = { selectedOptionKey = optionKey; expanded = false }
                    )
                }
            }
        }

        selected?.let { worker ->
            Spacer(Modifier.height(10.dp))
            SaymaCard {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Avatar(worker.name, 46)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(worker.name.ifBlank { "Unnamed worker" }, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Text(worker.role, color = Muted, fontSize = 12.sp)
                    }
                    Chip("SELECTED", Teal)
                }
                Spacer(Modifier.height(8.dp))
                InfoLine("Passport / ID", worker.passport)
                InfoLine("Assigned site", worker.site)
                InfoLine("Email", worker.email)
                InfoLine("Mobile", worker.mobile)
                InfoLine("Daily rate", rm(worker.rate))
                GradientButton("Open profile & edit", Modifier.fillMaxWidth()) { onOpen(worker) }
            }
        }

        SectionTitle("All workers (${filtered.size})")
        SaymaCard {
            if (filtered.isEmpty()) EmptyState("No matching workers", "Clear the search or refresh the worker data.")
            else filtered.forEach { (worker, optionKey) ->
                ListRow(
                    worker.name.ifBlank { "Unnamed worker" },
                    "${worker.role} · ${worker.passport.ifBlank { worker.email.ifBlank { "No ID" } }}",
                    rm(worker.rate), "/day", worker.name
                ) { selectedOptionKey = optionKey; onOpen(worker) }
            }
        }
    }
}

@Composable
fun WorkerDetailScreen(data: AdminData, w: WorkerRec, year: String, onRefresh: () -> Unit = {}, onBack: () -> Unit) {
    val api = apiOf()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf("") }
    var showEdit by remember { mutableStateOf(false) }
    var showUpload by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<org.json.JSONObject?>(null) }
    var saving by remember { mutableStateOf(false) }
    var editName by remember(w.key) { mutableStateOf(w.name) }
    var editRole by remember(w.key) { mutableStateOf(w.role) }
    var editSite by remember(w.key) { mutableStateOf(w.site) }
    var editRate by remember(w.key) { mutableStateOf(w.rate.toString()) }
    var editCidb by remember(w.key) { mutableStateOf(w.cidb) }
    var editPermitExpiry by remember(w.key) { mutableStateOf(w.permitExpiry) }
    var editCidbDate by remember(w.key) { mutableStateOf(w.cidbDate) }
    var editMobile by remember(w.key) { mutableStateOf(w.mobile) }
    var editEmail by remember(w.key) { mutableStateOf(w.email) }
    var docType by remember { mutableStateOf("Passport") }
    var uploadPart by remember { mutableStateOf<FilePart?>(null) }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uploadPart = uri?.let { readUri(context, it, "file") }
    }
    val (opener, host) = rememberFileOpener { err = it }
    val (docs, reload) = useLoad(w.key) { api.get("list_worker_documents", mapOf("worker_key" to w.key)).optJSONArray("documents").objects() }
    val p = data.permit(w.passport, year)
    host()

    ScreenScroll {
        BackBar(w.name, "${w.role} · ${w.passport.ifBlank { "No passport" }}", onBack)
        SaymaCard {
            InfoLine("Site", w.site); InfoLine("Daily rate", rm(w.rate)); InfoLine("Mobile", w.mobile); InfoLine("Email", w.email)
            InfoLine("CIDB", w.cidb); InfoLine("Permit expiry", w.permitExpiry); InfoLine("CIDB date", w.cidbDate)
            Spacer(Modifier.height(8.dp))
            SoftButton("✎ Edit worker profile", Modifier.fillMaxWidth()) { showEdit = true }
        }
        SectionTitle("Permit payments · $year")
        SaymaCard {
            InfoLine("Target", rm(p.target)); InfoLine("Paid", rm(p.paid)); InfoLine("Remaining", rm(Math.max(0.0, p.target - p.paid)))
            ProgressBar(if (p.target > 0) (p.paid / p.target).toFloat() else 0f, Modifier.padding(top = 8.dp))
            p.costItems.forEach { InfoLine(it.str("part"), rm(it.num("amount"))) }
        }
        SectionTitle("Worker documents") {
            SoftButton("+ Upload document") { uploadPart = null; showUpload = true }
        }
        if (err.isNotBlank()) Text(err, color = Bad)
        SaymaCard {
            Loaded(docs, reload) { list ->
                if (list.isEmpty()) EmptyState("No documents uploaded")
                list.forEach { d ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Text(d.str("doc_type").ifBlank { "Document" }, fontWeight = FontWeight.Bold)
                        Text(d.str("original_name"), color = Muted, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { opener.open { api.file("view_worker_document", mapOf("id" to d.str("id")), d.str("original_name")) } }) { Text("Open") }
                            TextButton(onClick = { deleteTarget = d }) { Text("Delete", color = Bad) }
                        }
                        HorizontalDivider(color = Line)
                    }
                }
            }
        }
    }

    if (showEdit) {
        AlertDialog(
            onDismissRequest = { if (!saving) showEdit = false },
            title = { Text("Edit worker profile") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    LabeledField(editName, { editName = it }, "Full name")
                    LabeledField(editRole, { editRole = it }, "Role / position")
                    LabeledField(editSite, { editSite = it }, "Assigned project / site")
                    LabeledField(editRate, { editRate = it }, "Daily rate (RM)")
                    LabeledField(editCidb, { editCidb = it }, "CIDB number")
                    LabeledField(editPermitExpiry, { editPermitExpiry = it }, "Permit expiry (YYYY-MM-DD)")
                    LabeledField(editCidbDate, { editCidbDate = it }, "CIDB expiry (YYYY-MM-DD)")
                    LabeledField(editMobile, { editMobile = it }, "Mobile")
                    LabeledField(editEmail, { editEmail = it }, "Email")
                    Text("Passport / Worker ID is kept unchanged because it is the record key.", color = Muted, fontSize = 11.sp)
                    if (err.isNotBlank()) Text(err, color = Bad, fontSize = 12.sp)
                }
            },
            confirmButton = {
                GradientButton(if (saving) "Saving…" else "Save changes", enabled = !saving) {
                    if (editName.isBlank()) { err = "Worker name is required."; return@GradientButton }
                    scope.launch {
                        saving = true; err = ""
                        try {
                            saveWorkerProfile(api, w, mapOf(
                                "name" to editName.trim(), "role" to editRole.trim(), "site" to editSite.trim(),
                                "rate" to editRate.trim(), "cidb" to editCidb.trim(), "permitExpiry" to editPermitExpiry.trim(),
                                "cidbDate" to editCidbDate.trim(), "mobile" to editMobile.trim(), "email" to editEmail.trim()
                            ))
                            showEdit = false; onRefresh()
                        } catch (e: Exception) { err = e.message ?: "Worker update failed." }
                        saving = false
                    }
                }
            },
            dismissButton = { TextButton(onClick = { if (!saving) showEdit = false }) { Text("Cancel") } }
        )
    }

    if (showUpload) {
        AlertDialog(
            onDismissRequest = { showUpload = false },
            title = { Text("Upload worker document") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("For ${w.name} · ${w.passport}", color = Muted, fontSize = 12.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Passport", "Visa", "CIDB", "Work Permit", "Medical / FOMEMA", "Insurance", "Other").forEach { t ->
                            FilterChip(selected = docType == t, onClick = { docType = t }, label = { Text(t) })
                        }
                    }
                    SoftButton(uploadPart?.name ?: "Choose file", Modifier.fillMaxWidth()) { filePicker.launch(arrayOf("*/*")) }
                    Text("Maximum 15 MB. PDF, image or Office file.", color = Muted, fontSize = 11.sp)
                    if (err.isNotBlank()) Text(err, color = Bad, fontSize = 12.sp)
                }
            },
            confirmButton = {
                GradientButton("Upload and save", enabled = uploadPart != null && !saving) {
                    scope.launch {
                        saving = true; err = ""
                        try {
                            val part = uploadPart ?: throw ApiException("Choose a file.")
                            if (part.bytes.size > 15 * 1024 * 1024) throw ApiException("File exceeds 15 MB limit.")
                            api.post("upload_worker_document", mapOf(
                                "worker_key" to w.key, "worker_name" to w.name,
                                "passport_no" to w.passport, "doc_type" to docType
                            ), listOf(part))
                            showUpload = false; uploadPart = null; reload.trigger()
                        } catch (e: Exception) { err = e.message ?: "Upload failed." }
                        saving = false
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showUpload = false }) { Text("Cancel") } }
        )
    }

    deleteTarget?.let { doc ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete worker document?") },
            text = { Text(doc.str("original_name")) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try { api.post("delete_worker_document", mapOf("id" to doc.str("id"))); deleteTarget = null; reload.trigger() }
                        catch (e: Exception) { err = e.message ?: "Delete failed."; deleteTarget = null }
                    }
                }) { Text("Delete", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}
@Composable
internal fun InfoLine(label: String, value: String) {
    if (value.isBlank()) return
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = Muted, modifier = Modifier.weight(1f), fontSize = 13.sp)
        Text(value, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun SitesScreen(data: AdminData, onOpen: (ProjectRec) -> Unit) {
    ScreenScroll {
        ScreenHeader("Projects", "Sites, claims & salary sheets")
        if (data.projects.isEmpty()) EmptyState("No projects", "Add your first site from the website.")
        data.projects.forEach { p ->
            SaymaCard(Modifier.padding(bottom = 10.dp), onClick = { onOpen(p) }) {
                Chip(p.status.ifBlank { "—" }, statusColor(p.status))
                Text(p.name, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Text("📍 ${p.location.ifBlank { "—" }}", color = Muted)
                Text("Site supervisor: ${p.supervisor.ifBlank { "—" }}")
            }
        }
    }
}

@Composable
fun ProjectDetailScreen(data: AdminData, p: ProjectRec, onRefresh: () -> Unit = {}, onBack: () -> Unit) {
    val api = apiOf()
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf("") }
    var showEdit by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var editLocation by remember(p.name) { mutableStateOf(p.location) }
    var editStatus by remember(p.name) { mutableStateOf(p.status) }
    var editSupervisor by remember(p.name) { mutableStateOf(p.supervisor) }
    val (opener, host) = rememberFileOpener { err = it }
    val mine = data.sheets.filter { it.project.equals(p.name, true) }
    val months = mine.map { it.month }.distinct().sortedBy { MONTHS.indexOf(it).let { i -> if (i < 0) 99 else i } }
    var month by remember { mutableStateOf(months.firstOrNull() ?: MONTHS[java.time.LocalDate.now().monthValue - 1]) }
    var type by remember { mutableStateOf("claims") }
    val sheet = mine.firstOrNull { it.month == month && it.type == type }
    val (pdocs, reloadDocs) = useLoad(p.name + month) { api.get("list_project_documents", mapOf("project" to p.name, "month" to month)).optJSONArray("items").objects() }
    host()
    ScreenScroll {
        BackBar(p.name, "${p.location} · ${p.status}", onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SoftButton("✎ Edit project details") { showEdit = true }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (months.ifEmpty { listOf(month) }).forEach { m -> FilterChip(selected = m == month, onClick = { month = m }, label = { Text(m) }) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            FilterChip(selected = type == "claims", onClick = { type = "claims" }, label = { Text("Claims") })
            FilterChip(selected = type == "salary", onClick = { type = "salary" }, label = { Text("Salary") })
        }
        if (err.isNotBlank()) Text(err, color = Bad)
        SaymaCard {
            if (sheet == null || sheet.rows.isEmpty()) EmptyState("এই মাসের ${if (type == "claims") "Claims" else "Salary"} শিট খালি")
            else {
                val claimCol = sheet.headers.indexOfFirst { it.trim().equals("claim id", true) }
                Column(Modifier.horizontalScroll(rememberScrollState())) {
                    Row { sheet.headers.forEach { h -> Text(h, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Teal, modifier = Modifier.width(110.dp).padding(4.dp)) }; if (claimCol >= 0) Text("PDF", Modifier.width(70.dp).padding(4.dp), fontWeight = FontWeight.Bold, fontSize = 11.sp) }
                    sheet.rows.forEach { row ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            sheet.headers.indices.forEach { i -> Text(row.getOrElse(i) { "" }, fontSize = 12.sp, modifier = Modifier.width(110.dp).padding(4.dp)) }
                            if (claimCol >= 0) {
                                val cid = row.getOrElse(claimCol) { "" }
                                if (cid.isNotBlank()) TextButton(onClick = { opener.open { api.file("view_claim_pdf", mapOf("claim_id" to cid), "$cid.pdf") } }, modifier = Modifier.width(70.dp)) { Text("View", fontSize = 12.sp) }
                            }
                        }
                        HorizontalDivider(color = Line)
                    }
                }
            }
        }
        SectionTitle("Project documents · $month")
        SaymaCard {
            Loaded(pdocs, reloadDocs) { list ->
                if (list.isEmpty()) EmptyState("No project documents")
                list.forEach { d ->
                    ListRow(d.str("document_type").ifBlank { "Document" }, d.str("original_name"), "Open", "") {
                        opener.open { api.file("view_project_document", mapOf("id" to d.str("id")), d.str("original_name")) }
                    }
                }
            }
        }
    }
    if (showEdit) {
        AlertDialog(
            onDismissRequest = { if (!saving) showEdit = false },
            title = { Text("Edit project details") },
            text = {
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(p.name, fontWeight = FontWeight.Bold)
                    LabeledField(editLocation, { editLocation = it }, "Project location")
                    LabeledField(editStatus, { editStatus = it }, "Project status")
                    LabeledField(editSupervisor, { editSupervisor = it }, "Site supervisor")
                    Text("Project name is kept unchanged so existing claims and salary sheets remain linked.", color = Muted, fontSize = 11.sp)
                    if (err.isNotBlank()) Text(err, color = Bad, fontSize = 12.sp)
                }
            },
            confirmButton = {
                GradientButton(if (saving) "Saving…" else "Save changes", enabled = !saving) {
                    scope.launch {
                        saving = true; err = ""
                        try {
                            saveProjectProfile(api, p, editLocation.trim(), editStatus.trim(), editSupervisor.trim())
                            showEdit = false; onRefresh()
                        } catch (e: Exception) { err = e.message ?: "Project update failed." }
                        saving = false
                    }
                }
            },
            dismissButton = { TextButton(onClick = { if (!saving) showEdit = false }) { Text("Cancel") } }
        )
    }
}
@Composable
fun FinanceScreen(data: AdminData, year: String, onYear: (String) -> Unit) {
    val (due, paid, remaining) = data.totals(year)
    ScreenScroll {
        ScreenHeader("Finance", "Permit payments & payroll overview")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { data.years.forEach { y -> FilterChip(selected = y == year, onClick = { onYear(y) }, label = { Text(y) }) } }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.linearGradient(listOf(Color(0xFF173454), Color(0xFF24639A), Color(0xFF5A8FB3)))).padding(20.dp)) {
            Text("PERMIT BUDGET · $year", color = Color.White.copy(alpha = .78f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Text(rm(due), color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.ExtraBold)
            Text("${rm(paid)} paid · ${rm(remaining)} outstanding", color = Color.White.copy(alpha = .86f), fontSize = 12.sp)
            ProgressBar(if (due > 0) (paid / due).toFloat() else 0f, Modifier.padding(top = 14.dp))
        }
        SectionTitle("Permit ledger")
        SaymaCard {
            if (data.workers.isEmpty()) EmptyState("No workers")
            data.workers.forEach { w ->
                val p = data.permit(w.passport, year)
                ListRow(w.name, "Paid ${rm(p.paid)} of ${rm(p.target)}", rm(Math.max(0.0, p.target - p.paid)), "remaining", w.name)
            }
        }
    }
}

@Composable
fun RemindersScreen(data: AdminData, onBack: () -> Unit) {
    val items = expiries(data, 90)
    ScreenScroll {
        BackBar("Expiry reminders", "Permit and CIDB due dates", onBack)
        SaymaCard { if (items.isEmpty()) EmptyState("কোনো মেয়াদ শেষের সতর্কতা নেই"); items.forEach { ExpiryRow(it) } }
    }
}


private suspend fun saveWorkerProfile(api: com.sayma.builders.data.Api, worker: WorkerRec, values: Map<String, String>) {
    val root = api.get("get_state").optJSONObject("state") ?: throw ApiException("Could not load current company data.")
    val rows = root.optJSONArray("workers") ?: throw ApiException("Worker records are unavailable.")
    val target = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.firstOrNull {
        it.str("passport").ifBlank { it.str("name") }.trim().uppercase() == worker.key
    } ?: throw ApiException("Worker record was not found.")
    target.put("name", values["name"] ?: worker.name)
    target.put("role", values["role"] ?: worker.role)
    target.put("site", values["site"] ?: worker.site)
    target.put("rate", values["rate"]?.toDoubleOrNull() ?: target.optDouble("rate", 0.0))
    target.put("cidb", values["cidb"] ?: worker.cidb)
    target.put("permitExpiry", values["permitExpiry"] ?: worker.permitExpiry)
    target.put("cidbDate", values["cidbDate"] ?: worker.cidbDate)
    val mobile = values["mobile"] ?: worker.mobile
    target.put("mobile", mobile)
    if (target.has("phone")) target.put("phone", mobile)
    target.put("email", values["email"] ?: worker.email)
    api.post("save_state", mapOf("state_json" to root.toString()))
}

private suspend fun saveProjectProfile(api: com.sayma.builders.data.Api, project: ProjectRec, location: String, status: String, supervisor: String) {
    val root = api.get("get_state").optJSONObject("state") ?: throw ApiException("Could not load current company data.")
    val rows = root.optJSONArray("projects") ?: throw ApiException("Project records are unavailable.")
    val target = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.firstOrNull {
        it.str("name").equals(project.name, true)
    } ?: throw ApiException("Project record was not found.")
    target.put("location", location)
    target.put("status", status)
    target.put("supervisor", supervisor)
    api.post("save_state", mapOf("state_json" to root.toString()))
}
