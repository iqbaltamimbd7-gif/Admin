package com.sayma.builders

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.JavascriptInterface
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.webkit.WebViewClient
import android.webkit.DownloadListener
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.view.View
import android.graphics.Color
import android.webkit.SslErrorHandler
import android.net.http.SslError
import android.webkit.WebResourceError
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SAYMA Android hybrid shell.
 *
 * The existing HTTPS portal remains the source of truth. Android provides
 * native WebView geolocation permission handling, camera/file selection,
 * persistent cookies and downloads. The portal's service worker supplies the
 * offline static shell; the web offline queue/sync logic is delivered with the
 * matching portal source patch.
 */
class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null
    private var pendingGeoOrigin: String? = null
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var pendingCameraUri: Uri? = null

    private val locationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val granted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        pendingGeoCallback?.invoke(pendingGeoOrigin, granted, false)
        pendingGeoCallback = null
        pendingGeoOrigin = null
        if (!granted) Toast.makeText(this, "Allow location access to use GPS attendance.", Toast.LENGTH_LONG).show()
    }

    private val fileChooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = uploadCallback
        uploadCallback = null
        val selected = when {
            result.resultCode != RESULT_OK -> null
            result.data?.clipData != null -> {
                val clip = result.data!!.clipData!!
                Array(clip.itemCount) { i -> clip.getItemAt(i).uri }
            }
            result.data?.data != null -> arrayOf(result.data!!.data!!)
            pendingCameraUri != null -> arrayOf(pendingCameraUri!!)
            else -> null
        }
        callback?.onReceiveValue(selected)
        pendingCameraUri = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(7, 18, 34)
        window.navigationBarColor = Color.rgb(7, 18, 34)

        val root = FrameLayout(this)
        webView = WebView(this)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(34, 166, 145))
            indeterminateTintList = android.content.res.ColorStateList.valueOf(Color.rgb(34, 166, 145))
        }
        root.addView(webView, FrameLayout.LayoutParams(-1, -1))
        root.addView(progress, FrameLayout.LayoutParams(-1, dp(3)))
        setContentView(root)

        configureWebView()
        webView.addJavascriptInterface(NativeBridge(), "SaymaNative")
        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(PORTAL_LOGIN)
        }
    }

    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setGeolocationEnabled(true)
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "https" && uri.host == PORTAL_HOST) return false
                if (uri.scheme == "http" && uri.host == PORTAL_HOST) {
                    view.loadUrl(uri.buildUpon().scheme("https").build().toString())
                    return true
                }
                if (uri.scheme == "mailto" || uri.scheme == "tel" || uri.scheme == "geo") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }
                if (uri.scheme == "https") {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    return true
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                progress.progress = 8
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.progress = 100
                progress.postDelayed({ progress.visibility = View.GONE }, 180)
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                if (request.isForMainFrame && !hasInternet()) {
                    view.loadDataWithBaseURL(
                        PORTAL_BASE,
                        OFFLINE_FALLBACK_HTML,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                if (newProgress >= 100) progress.visibility = View.GONE else progress.visibility = View.VISIBLE
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                if (origin == null || !origin.startsWith(PORTAL_ORIGIN)) {
                    callback?.invoke(origin, false, false)
                    return
                }
                val fine = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                val coarse = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (fine || coarse) {
                    callback?.invoke(origin, true, false)
                } else {
                    pendingGeoCallback = callback
                    pendingGeoOrigin = origin
                    locationPermission.launch(arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ))
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                uploadCallback?.onReceiveValue(null)
                uploadCallback = filePathCallback
                val params = fileChooserParams ?: return false
                try {
                    val accept = params.acceptTypes.flatMap { it.split(",") }.map { it.trim() }.filter { it.isNotEmpty() }
                    val mime = when {
                        accept.isEmpty() -> "*/*"
                        accept.size == 1 -> accept[0]
                        else -> "*/*"
                    }
                    val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = mime
                        if (accept.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, accept.toTypedArray())
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, params.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                    }
                    val chooser = Intent.createChooser(pick, "Choose a file")
                    if (accept.any { it.startsWith("image/") } && params.isCaptureEnabled) {
                        val camera = createCameraIntent()
                        if (camera != null) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(camera))
                    }
                    fileChooser.launch(chooser)
                    return true
                } catch (_: Exception) {
                    uploadCallback = null
                    return false
                }
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .setTitle(downloadName(contentDisposition, url))
                    .setDescription("Downloading from SAYMA BUILDERS")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, downloadName(contentDisposition, url))
                    .addRequestHeader("User-Agent", userAgent ?: webView.settings.userAgentString)
                CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
                val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                manager.enqueue(request)
                Toast.makeText(this, "Download started", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, e.message ?: "Download could not start", Toast.LENGTH_LONG).show()
            }
        })

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
    }

    /**
     * Encrypts offline JSON snapshots and queued file payloads with an AES-GCM
     * key held by Android Keystore. The key never leaves this device.
     */
    private inner class NativeBridge {
        @JavascriptInterface
        fun encrypt(plainText: String): String {
            return try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, offlineKey())
                val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val combined = cipher.iv + encrypted
                Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (e: Exception) { "" }
        }

        @JavascriptInterface
        fun decrypt(cipherText: String): String {
            return try {
                val combined = Base64.decode(cipherText, Base64.NO_WRAP)
                if (combined.size <= 12) return ""
                val iv = combined.copyOfRange(0, 12)
                val encrypted = combined.copyOfRange(12, combined.size)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, offlineKey(), GCMParameterSpec(128, iv))
                String(cipher.doFinal(encrypted), Charsets.UTF_8)
            } catch (e: Exception) { "" }
        }

        @JavascriptInterface
        fun isNative(): Boolean = true
    }

    private fun offlineKey(): SecretKey {
        val alias = "sayma.builders.offline.cache.key.v1"
        val keyStore = KeyStore.getInstance("AndroidKeyStore")
        keyStore.load(null)
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(false)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }
    private fun createCameraIntent(): Intent? {
        return try {
            val folder = File(cacheDir, "portal-captures").apply { mkdirs() }
            val file = File(folder, "sayma-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())}.jpg")
            pendingCameraUri = FileProvider.getUriForFile(this, "${packageName}.files", file)
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(android.provider.MediaStore.EXTRA_OUTPUT, pendingCameraUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun hasInternet(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun downloadName(disposition: String?, url: String): String {
        val fromHeader = disposition?.let { Regex("filename[^=]*=([^;]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.getOrNull(1)?.trim()?.trim('"') }
        val raw = fromHeader ?: Uri.parse(url).lastPathSegment ?: "sayma-download"
        return URLDecoder.decode(raw, "UTF-8").replace(Regex("[^A-Za-z0-9._ -]"), "_").take(120).ifBlank { "sayma-download" }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val PORTAL_HOST = "saymabuilders.com"
        private const val PORTAL_ORIGIN = "https://saymabuilders.com"
        private const val PORTAL_BASE = "https://saymabuilders.com/portal/"
        private const val PORTAL_LOGIN = "https://saymabuilders.com/portal/login.html?next=mobile"
        private const val OFFLINE_FALLBACK_HTML = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <meta name="theme-color" content="#071222"><style>
            *{box-sizing:border-box}body{margin:0;min-height:100vh;display:grid;place-items:center;
            padding:24px;background:linear-gradient(145deg,#071222,#17334a);color:#fff;font:15px system-ui}
            main{max-width:380px;padding:28px;border:1px solid #ffffff22;border-radius:24px;
            background:#ffffff10;text-align:center}h1{font-size:22px;margin:0 0 10px}
            p{color:#c7d4e4;line-height:1.6;font-size:13px}button{border:0;border-radius:12px;
            padding:13px 20px;background:#22a691;color:#fff;font-weight:800}
            </style></head><body><main><h1>SAYMA BUILDERS</h1>
            <p>No saved portal screen is available yet. Connect to the internet once to load the portal and prepare offline access.</p>
            <button onclick="location.href='https://saymabuilders.com/portal/login.html?next=mobile'">Try again</button>
            </main></body></html>
        """
    }
}
