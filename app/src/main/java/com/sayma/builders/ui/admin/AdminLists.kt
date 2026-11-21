package com.sayma.builders.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.data.*
import com.sayma.builders.ui.*

@Composable
fun WorkersScreen(data: AdminData, onOpen: (WorkerRec) -> Unit) {
    var q by remember { mutableStateOf("") }
    val list = data.workers.filter { "${it.name} ${it.role} ${it.passport} ${it.site}".contains(q, true) }
    ScreenScroll {
        ScreenHeader("Workers", "${data.workers.size} workforce profiles")
        LabeledField(q, { q = it }, "Search worker, role or passport…")
        SectionTitle("Team directory")
        SaymaCard {
            if (list.isEmpty()) EmptyState("No matching workers")
            list.forEach { w -> ListRow(w.name, "${w.role} · ${w.passport.ifBlank { "No passport" }}", rm(w.rate), "/day", w.name) { onOpen(w) } }
        }
    }
}

@Composable
fun WorkerDetailScreen(data: AdminData, w: WorkerRec, year: String, onBack: () -> Unit) {
    val api = apiOf()
    var err by remember { mutableStateOf("") }
    val (opener, host) = rememberFileOpener { err = it }
    val (docs, reload) = useLoad(w.key) { api.get("list_worker_documents", mapOf("worker_key" to w.key)).optJSONArray("documents").objects() }
    val p = data.permit(w.passport, year)
    host()
    ScreenScroll {
        BackBar(w.name, "${w.role} · ${w.passport.ifBlank { "No passport" }}", onBack)
        SaymaCard {
            InfoLine("Site", w.site); InfoLine("Daily rate", rm(w.rate)); InfoLine("Mobile", w.mobile); InfoLine("Email", w.email)
            InfoLine("CIDB", w.cidb); InfoLine("Permit expiry", w.permitExpiry); InfoLine("CIDB date", w.cidbDate)
        }
        SectionTitle("Permit payments · $year")
        SaymaCard {
            InfoLine("Target", rm(p.target)); InfoLine("Paid", rm(p.paid)); InfoLine("Remaining", rm(Math.max(0.0, p.target - p.paid)))
            ProgressBar(if (p.target > 0) (p.paid / p.target).toFloat() else 0f, Modifier.padding(top = 8.dp))
            p.costItems.forEach { InfoLine(it.str("part"), rm(it.num("amount"))) }
        }
        SectionTitle("Worker documents")
        if (err.isNotBlank()) Text(err, color = Bad)
        SaymaCard {
            Loaded(docs, reload) { list ->
                if (list.isEmpty()) EmptyState("No documents uploaded")
                list.forEach { d ->
                    ListRow(d.str("doc_type").ifBlank { "Document" }, d.str("original_name"), "Open", "") {
                        opener.open { api.file("view_worker_document", mapOf("id" to d.str("id")), d.str("original_name")) }
                    }
                }
            }
        }
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
fun ProjectDetailScreen(data: AdminData, p: ProjectRec, onBack: () -> Unit) {
    val api = apiOf()
    var err by remember { mutableStateOf("") }
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
}

@Composable
fun FinanceScreen(data: AdminData, year: String, onYear: (String) -> Unit) {
    val (due, paid, remaining) = data.totals(year)
    ScreenScroll {
        ScreenHeader("Finance", "Permit payments & payroll overview")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { data.years.forEach { y -> FilterChip(selected = y == year, onClick = { onYear(y) }, label = { Text(y) }) } }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().then(Modifier).padding(0.dp)) {
            SaymaCard {
                Text("PERMIT BUDGET · $year", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(rm(due), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("${rm(paid)} paid · ${rm(remaining)} outstanding", color = Muted)
                ProgressBar(if (due > 0) (paid / due).toFloat() else 0f, Modifier.padding(top = 8.dp))
            }
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
