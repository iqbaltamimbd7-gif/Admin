package com.sayma.builders.data

import android.content.Context
import com.sayma.builders.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ApiException(message: String, val authRequired: Boolean = false, val mustSetPassword: Boolean = false) : Exception(message)

class FilePart(val field: String, val name: String, val mime: String, val bytes: ByteArray)

class FileData(val name: String, val mime: String, val bytes: ByteArray) {
    val isPdf get() = mime.contains("pdf", true) || name.endsWith(".pdf", true)
    val isImage get() = mime.startsWith("image/", true) || Regex(".*\\.(jpg|jpeg|png|webp|gif)$", RegexOption.IGNORE_CASE).matches(name)
}

/** Keeps the PHP session cookie across app restarts (the website cookie has no expiry date). */
private class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs = context.getSharedPreferences("sayma_cookies", Context.MODE_PRIVATE)
    private val store = HashMap<String, Cookie>()

    init {
        prefs.all.forEach { (k, v) ->
            val raw = v as? String ?: return@forEach
            val i = raw.indexOf('\n')
            if (i < 0) return@forEach
            val url = raw.substring(0, i).toHttpUrlOrNull() ?: return@forEach
            Cookie.parse(url, raw.substring(i + 1))?.let { store[k] = it }
        }
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val edit = prefs.edit()
        for (c in cookies) {
            val key = "${c.name}|${c.domain}|${c.path}"
            if (c.expiresAt < System.currentTimeMillis()) {
                store.remove(key); edit.remove(key)
            } else {
                store[key] = c
                edit.putString(key, url.toString() + "\n" + c.toString())
            }
        }
        edit.apply()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> = store.values.filter { it.matches(url) }

    @Synchronized
    fun clear() {
        store.clear(); prefs.edit().clear().apply()
    }
}

class Api(context: Context) {
    private val settings = context.getSharedPreferences("sayma_settings", Context.MODE_PRIVATE)
    private val jar = PersistentCookieJar(context)

    private val client = OkHttpClient.Builder()
        .cookieJar(jar)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    var baseUrl: String
        get() = settings.getString("base_url", null)?.takeIf { it.isNotBlank() } ?: BuildConfig.DEFAULT_BASE_URL
        set(value) {
            var v = value.trim()
            if (v.isNotEmpty() && !v.endsWith("/")) v += "/"
            settings.edit().putString("base_url", v).apply()
        }

    var rememberedRole: String
        get() = settings.getString("role", "") ?: ""
        set(value) { settings.edit().putString("role", value).apply() }

    private fun endpoint(action: String, params: Map<String, String> = emptyMap()): HttpUrl {
        val root = (baseUrl + "api.php").toHttpUrlOrNull()
            ?: throw ApiException("সার্ভার ঠিকানা সঠিক নয়। লগইন স্ক্রিনে Server অংশে ঠিকানা ঠিক করুন।")
        return root.newBuilder().addQueryParameter("action", action).apply {
            params.forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
    }

    private fun parse(code: Int, body: String): JSONObject {
        val json = try { JSONObject(body) } catch (e: Exception) {
            throw ApiException("সার্ভার থেকে সঠিক উত্তর আসেনি (HTTP $code)। Server ঠিকানা ও /portal/ ফোল্ডার যাচাই করুন।")
        }
        if (!json.optBoolean("ok", false)) {
            throw ApiException(
                json.optString("error", "অনুরোধ ব্যর্থ হয়েছে (HTTP $code)"),
                authRequired = json.optBoolean("auth_required", false) || code == 401,
                mustSetPassword = json.optBoolean("must_set_password", false)
            )
        }
        return json
    }

    private fun network(e: Exception): ApiException =
        if (e is ApiException) e else ApiException("ইন্টারনেট সংযোগ বা সার্ভারে সমস্যা: ${e.message ?: "অজানা"}")

    suspend fun get(action: String, params: Map<String, String> = emptyMap()): JSONObject =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(endpoint(action, params)).header("Accept", "application/json").build())
                    .execute().use { parse(it.code, it.body?.string() ?: "") }
            } catch (e: Exception) { throw network(e) }
        }

    suspend fun post(action: String, fields: Map<String, String> = emptyMap(), files: List<FilePart> = emptyList()): JSONObject =
        withContext(Dispatchers.IO) {
            try {
                val body = if (files.isEmpty()) {
                    FormBody.Builder().add("action", action).apply { fields.forEach { (k, v) -> add(k, v) } }.build()
                } else {
                    MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart("action", action).apply {
                        fields.forEach { (k, v) -> addFormDataPart(k, v) }
                        files.forEach { f ->
                            addFormDataPart(f.field, f.name, f.bytes.toRequestBody((f.mime.ifBlank { "application/octet-stream" }).toMediaTypeOrNull()))
                        }
                    }.build()
                }
                client.newCall(Request.Builder().url(endpoint(action)).header("Accept", "application/json").post(body).build())
                    .execute().use { parse(it.code, it.body?.string() ?: "") }
            } catch (e: Exception) { throw network(e) }
        }

    /** Downloads a private document (PDF / image) using the logged-in session. */
    suspend fun file(action: String, params: Map<String, String>, fallbackName: String = "SAYMA-document"): FileData =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(Request.Builder().url(endpoint(action, params)).build()).execute().use { r ->
                    val mime = (r.header("Content-Type") ?: "").substringBefore(';').trim()
                    val bytes = r.body?.bytes() ?: ByteArray(0)
                    if (!r.isSuccessful || mime.contains("json", true)) {
                        val msg = try { JSONObject(String(bytes)).optString("error") } catch (e: Exception) { "" }
                        throw ApiException(
                            msg.ifBlank {
                                when (r.code) {
                                    401, 403 -> "ফাইল দেখতে আবার সাইন ইন করুন।"
                                    404 -> "ফাইলটি পাওয়া যায়নি।"
                                    else -> "ফাইল লোড করা যায়নি (HTTP ${r.code})।"
                                }
                            }, authRequired = r.code == 401
                        )
                    }
                    val disp = r.header("Content-Disposition") ?: ""
                    val name = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)
                        .find(disp)?.groupValues?.get(1)?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: fallbackName
                    FileData(name, mime, bytes)
                }
            } catch (e: Exception) { throw network(e) }
        }

    suspend fun logout(role: String) {
        try { post(if (role == "worker") "worker_logout" else "logout") } catch (_: Exception) {}
        jar.clear()
        rememberedRole = ""
    }
}
