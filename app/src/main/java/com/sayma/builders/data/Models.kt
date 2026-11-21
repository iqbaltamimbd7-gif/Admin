package com.sayma.builders.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "").let { if (it == "null") "" else it }
fun JSONObject.num(key: String): Double = optDouble(key, 0.0).let { if (it.isNaN()) 0.0 else it }

fun rm(v: Double): String = "RM " + NumberFormat.getNumberInstance(Locale.US).apply {
    minimumFractionDigits = 2; maximumFractionDigits = 2
}.format(v)

fun shortRm(v: Double): String {
    val a = Math.abs(v)
    return "RM " + when {
        a >= 1_000_000 -> String.format(Locale.US, "%.2fm", v / 1_000_000)
        a >= 1_000 -> String.format(Locale.US, "%.1fk", v / 1_000)
        else -> Math.round(v).toString()
    }
}

val MONTHS = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

class WorkerRec(val raw: JSONObject) {
    val name get() = raw.str("name")
    val passport get() = raw.str("passport")
    val role get() = raw.str("role").ifBlank { "Worker" }
    val site get() = raw.str("site")
    val rate get() = raw.num("rate")
    val cidb get() = raw.str("cidb")
    val permitExpiry get() = raw.str("permitExpiry")
    val cidbDate get() = raw.str("cidbDate")
    val mobile get() = raw.str("mobile").ifBlank { raw.str("phone") }
    val email get() = raw.str("email")
    val key get() = passport.ifBlank { name }.trim().uppercase()
}

class ProjectRec(val raw: JSONObject) {
    val name get() = raw.str("name")
    val location get() = raw.str("location")
    val status get() = raw.str("status")
    val supervisor get() = raw.str("supervisor")
}

class PermitRec(val raw: JSONObject?) {
    val target get() = raw?.num("target") ?: 0.0
    val paid get() = raw?.num("paid") ?: 0.0
    val costItems get() = raw?.optJSONArray("costItems").objects()
    val entries get() = raw?.optJSONArray("entries").objects()
}

class SheetRec(val project: String, val month: String, val type: String, val headers: List<String>, val rows: List<List<String>>)

/** Everything the admin dashboard needs, loaded from the same API actions the website uses. */
class AdminData(
    val workers: List<WorkerRec>,
    val projects: List<ProjectRec>,
    val permits: Map<String, Map<String, JSONObject>>, // passport -> year -> record
    val years: List<String>,
    val sheets: List<SheetRec>
) {
    fun permit(passport: String, year: String) = PermitRec(permits[passport]?.get(year))
    fun totals(year: String): Triple<Double, Double, Double> {
        var due = 0.0; var paid = 0.0
        workers.forEach { val p = permit(it.passport, year); due += p.target; paid += p.paid }
        return Triple(due, paid, Math.max(0.0, due - paid))
    }
}

suspend fun loadAdminData(api: Api): AdminData {
    val state = api.get("get_state").optJSONObject("state") ?: JSONObject()
    val workers = state.optJSONArray("workers").objects().map { WorkerRec(it) }
    val projects = state.optJSONArray("projects").objects().map { ProjectRec(it) }

    val permits = HashMap<String, MutableMap<String, JSONObject>>()
    val legacy = state.optJSONObject("permitPayments")
    legacy?.keys()?.forEach { p ->
        val ys = legacy.optJSONObject(p) ?: return@forEach
        ys.keys().forEach { y -> ys.optJSONObject(y)?.let { permits.getOrPut(p) { HashMap() }[y] = it } }
    }
    val years = sortedSetOf<String>()
    try {
        val ledger = api.get("get_worker_payment_ledger")
        val recs = ledger.optJSONObject("records")
        recs?.keys()?.forEach { p ->   // dedicated ledger wins over legacy state, as on the website
            val ys = recs.optJSONObject(p) ?: return@forEach
            ys.keys().forEach { y -> ys.optJSONObject(y)?.let { permits.getOrPut(p) { HashMap() }[y] = it } }
        }
        ledger.optJSONArray("years")?.let { a -> for (i in 0 until a.length()) years.add(a.optString(i)) }
    } catch (_: ApiException) {}
    state.optJSONArray("permitYears")?.let { a -> for (i in 0 until a.length()) years.add(a.optString(i)) }
    permits.values.forEach { years.addAll(it.keys) }
    val yearList = years.filter { Regex("\\d{4}").matches(it) && it != "2025" && it != "2027" }.ifEmpty { listOf("2026") }

    val sheets = ArrayList<SheetRec>()
    try {
        api.get("get_project_financials").optJSONArray("items").objects().forEach { r ->
            val type = r.str("data_type")
            if (type != "claims" && type != "salary") return@forEach
            val d = r.optJSONObject("data") ?: return@forEach
            val headers = d.optJSONArray("headers")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList()
            val rows = d.optJSONArray("rows")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    a.optJSONArray(i)?.let { row -> (0 until row.length()).map { c -> row.opt(c)?.toString()?.takeIf { s -> s != "null" } ?: "" } }
                }
            } ?: emptyList()
            sheets.add(SheetRec(r.str("project_name"), r.str("month_name"), type, headers, rows))
        }
    } catch (_: ApiException) {}
    return AdminData(workers, projects, permits, yearList, sheets)
}
