package com.sayma.builders.ui.admin

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sayma.builders.data.*
import com.sayma.builders.ui.*
import java.time.LocalDate
import java.time.temporal.ChronoUnit

internal data class Expiry(val worker: String, val label: String, val days: Long)

internal fun expiries(data: AdminData, withinDays: Long): List<Expiry> {
    val today = LocalDate.now()
    val out = ArrayList<Expiry>()
    data.workers.forEach { w ->
        listOf("Permit" to w.permitExpiry, "CIDB" to w.cidbDate).forEach { (label, raw) ->
            val d = try { LocalDate.parse(raw.take(10)) } catch (_: Exception) { null } ?: return@forEach
            val days = ChronoUnit.DAYS.between(today, d)
            if (days <= withinDays) out.add(Expiry(w.name, label, days))
        }
    }
    return out.sortedBy { it.days }
}

@Composable
fun HomeScreen(data: AdminData, displayName: String, year: String, onYear: (String) -> Unit, open: (AdminScreen) -> Unit, openWorkers: () -> Unit) {
    val (due, paid, remaining) = data.totals(year)
    val activeSites = data.projects.count { it.status.contains("active", true) }
    val soon = expiries(data, 60)
    val trades = data.workers.groupBy { it.role }.map { it.key to it.value.size }.sortedByDescending { it.second }

    ScreenScroll {
        ScreenHeader("Hi, ${displayName.substringBefore(' ')}.", "Your SAYMA workspace", profileName = displayName)

        SearchPill("", {}, "Search workers, passport, project…")
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(HeroBrush).padding(20.dp)) {
            Text("● SAYMA BUILDERS · ADMIN WORKSPACE", color = Color.White.copy(alpha = .9f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Text("Your projects.\nYour people. All in one place.", color = Color.White, fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            Text("Stay on top of your workforce, site activity and company performance.", color = Color.White.copy(alpha = .92f), fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                HeroPill("${data.workers.size} Workers"); HeroPill("$activeSites Active sites"); HeroPill("Connected")
            }
        }

        SectionTitle("Quick Links") { Text("Tap to open", color = Muted, fontSize = 11.sp) }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            QuickTile("Workers", "♙", Color(0xFF9B4CC0), Modifier.weight(1f), openWorkers)
            QuickTile("Projects", "⌂", Color(0xFF278FE2), Modifier.weight(1f)) { if (data.projects.isNotEmpty()) open(AdminScreen.ProjectDetail(data.projects.first())) }
            QuickTile("Attendance", "◷", Color(0xFF36AE98), Modifier.weight(1f)) { open(AdminScreen.Attendance) }
            QuickTile("Payments", "৳", Color(0xFFE34D63), Modifier.weight(1f)) { open(AdminScreen.Requests) }
        }

        SectionTitle("Permit payment collection") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                data.years.forEach { y -> FilterChip(selected = y == year, onClick = { onYear(y) }, label = { Text(y) }) }
            }
        }
        PermitChart(data, year)

        SectionTitle("Executive summary")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Active workforce", "${data.workers.size}", Modifier.weight(1f))
            StatTile("Permit collected", "${if (due > 0) Math.round(paid / due * 100) else 0}% · ${shortRm(paid)}", Modifier.weight(1f), Good)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Due ≤ 60 days", "${soon.size}", Modifier.weight(1f), if (soon.isEmpty()) Good else Warn)
            StatTile("Outstanding", shortRm(remaining), Modifier.weight(1f), Rose)
        }

        SectionTitle("Project financials")
        ClaimsSalaryChart(data)

        SectionTitle("Workforce by trade")
        SaymaCard {
            if (trades.isEmpty()) Text("Add a worker to see the breakdown.", color = Muted)
            trades.take(5).forEach { (role, count) ->
                Row { Text(role, Modifier.weight(1f)); Text("$count", fontWeight = FontWeight.Bold) }
                ProgressBar(count.toFloat() / data.workers.size.coerceAtLeast(1), Modifier.padding(vertical = 6.dp))
            }
        }

        if (soon.isNotEmpty()) {
            SectionTitle("Expiry alerts") { TextButton(onClick = { open(AdminScreen.Reminders) }) { Text("সব দেখুন") } }
            SaymaCard { soon.take(4).forEach { ExpiryRow(it) } }
        }

        SectionTitle("Your projects")
        if (data.projects.isEmpty()) EmptyState("No projects added yet")
        data.projects.forEach { p ->
            SaymaCard(Modifier.padding(bottom = 8.dp), onClick = { open(AdminScreen.ProjectDetail(p)) }) {
                Chip(p.status.ifBlank { "Project" }, statusColor(p.status))
                Text(p.name, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                Text("⌁ ${p.location.ifBlank { "Location not set" }}", color = Muted, fontSize = 12.sp)
                Text("Supervisor: ${p.supervisor.ifBlank { "Not assigned" }}", fontSize = 12.sp)
            }
        }
    }
}

@Composable private fun HeroPill(text: String) =
    Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .2f)).padding(horizontal = 12.dp, vertical = 5.dp))

@Composable
private fun QuickTile(label: String, glyph: String, accent: Color, modifier: Modifier, onClick: () -> Unit) =
    SaymaCard(modifier.height(112.dp), onClick = onClick) {
        Box(
            Modifier.size(37.dp).clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = .72f)))),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, fontWeight = FontWeight.ExtraBold, color = Ink, fontSize = 11.sp, maxLines = 1)
        Text(when(label) { "Workers" -> "Team directory"; "Projects" -> "Site management"; "Attendance" -> "Daily records"; else -> "Finance overview" }, color = Muted, fontSize = 9.sp, maxLines = 2)
    }

@Composable
internal fun ExpiryRow(e: Expiry) {
    ListRow(e.worker, "${e.label} expiry", if (e.days < 0) "${-e.days} days overdue" else if (e.days == 0L) "Today" else "in ${e.days} days", "")
}

@Composable
private fun PermitChart(data: AdminData, year: String) {
    var mode by remember { mutableStateOf("paid") }
    val (due, paid, _) = data.totals(year)
    val actual = data.workers.map { val p = data.permit(it.passport, year); if (mode == "paid") p.paid else p.target }
    SaymaCard {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = mode == "paid", onClick = { mode = "paid" }, label = { Text("Paid") })
            FilterChip(selected = mode == "target", onClick = { mode = "target" }, label = { Text("Target") })
        }
        Text("${shortRm(paid)} of ${shortRm(due)} collected", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        if (actual.isEmpty()) { Text("No workers yet.", color = Muted); return@SaymaCard }
        val ceil = (Math.ceil(((actual.maxOrNull() ?: 1.0).coerceAtLeast(1.0)) / 1000.0) * 1000.0).coerceAtLeast(1.0)
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val w = size.width; val h = size.height
            fun x(i: Int) = if (actual.size < 2) w / 2 else w * i / (actual.size - 1)
            fun y(v: Double) = (h - 6f - (h - 12f) * (v / ceil)).toFloat()
            listOf(0.0, .5, 1.0).forEach { f -> drawLine(Line, Offset(0f, y(ceil * f)), Offset(w, y(ceil * f)), 2f) }
            val area = Path().apply {
                moveTo(x(0), h); actual.forEachIndexed { i, v -> lineTo(x(i), y(v)) }; lineTo(x(actual.size - 1), h); close()
            }
            drawPath(area, Orchid.copy(alpha = .15f))
            val line = Path().apply { actual.forEachIndexed { i, v -> if (i == 0) moveTo(x(i), y(v)) else lineTo(x(i), y(v)) } }
            drawPath(line, Orchid, style = Stroke(width = 5f))
            actual.forEachIndexed { i, v -> drawCircle(Color.White, 8f, Offset(x(i), y(v))); drawCircle(Orchid, 8f, Offset(x(i), y(v)), style = Stroke(4f)) }
        }
        Text("Scale: 0 – ${shortRm(ceil)} · ${actual.size} workers", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun ClaimsSalaryChart(data: AdminData) {
    fun total(type: String, month: String): Double = data.sheets.filter { it.type == type && it.month == month }.sumOf { sh ->
        val idx = sh.headers.indexOfFirst { h -> val l = h.lowercase(); if (type == "claims") l.contains("total") else l.contains("net pay") }
        sh.rows.sumOf { row ->
            val col = if (idx in row.indices) idx else row.size - 1
            row.getOrNull(col)?.replace(Regex("[,RMrm\\s]"), "")?.toDoubleOrNull() ?: 0.0
        }
    }
    val periods = data.sheets.map { it.month }.distinct().sortedBy { MONTHS.indexOf(it).let { i -> if (i < 0) 99 else i } }
    val chosen = periods.firstOrNull { m -> data.sheets.any { it.month == m && it.rows.isNotEmpty() } } ?: periods.firstOrNull()
    SaymaCard {
        if (chosen == null) { Text("Add a project claims or salary sheet to populate this graph", color = Muted); return@SaymaCard }
        val claim = total("claims", chosen); val salary = total("salary", chosen)
        Text("$chosen · Recorded project sheets", color = Muted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            StatTile("Total claims", rm(claim), Modifier.weight(1f)); StatTile("Recorded salary", rm(salary), Modifier.weight(1f), Rose)
        }
        val max = Math.max(Math.max(claim, salary), 1.0)
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val bw = size.width * .28f
            fun bar(v: Double, left: Float, c: Color) {
                val bh = if (v <= 0) 0f else Math.max(6f, (v / max * (size.height - 8f)).toFloat())
                drawRoundRect(c, Offset(left, size.height - bh), Size(bw, bh), CornerRadius(14f))
            }
            bar(claim, size.width * .15f, Teal); bar(salary, size.width * .57f, Rose)
        }
    }
}
