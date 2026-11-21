package com.saymabuilders.mobile;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.ContentValues;
import android.content.ClipData;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.JsResult;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.Collections;
import android.util.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String HOST = "saymabuilders.com";
    private static final String LOGIN_URL =
            "https://saymabuilders.com/portal/login.html?next=mobile";
    private static final String MOBILE_URL =
            "https://saymabuilders.com/portal/mobile/";
    private static final String WORKER_URL =
            "https://saymabuilders.com/portal/worker/index.html";
    private static final String MOBILE_PREFIX = "/portal/mobile";
    private static final String WORKER_PREFIX = "/portal/worker";
    private static final String PORTAL_PREFIX = "/portal";

    private static final int FILE_CHOOSER_REQUEST = 5107;
    private static final int GEO_PERMISSION_REQUEST = 6101;
    private static final int WEB_MEDIA_PERMISSION_REQUEST = 6102;
    private static final int CAMERA_PICKER_PERMISSION_REQUEST = 6103;

    /*
     * Local app-session flag.
     *
     * IMPORTANT:
     * This does NOT store the user's password.
     * It only remembers that the user successfully reached
     * the authenticated mobile area once.
     */
    private static final String PREFS_NAME = "sayma_app_session";
    private static final String KEY_HAS_LOGGED_IN = "has_logged_in";
    private static final String KEY_LAST_ROLE = "last_role";

    private WebView webView;
    private FrameLayout rootLayout;
    private ProgressBar loadingBar;
    private ValueCallback<Uri[]> filePathCallback;
    private WebChromeClient.FileChooserParams pendingFileChooserParams;
    private Uri pendingCameraImageUri;
    private GeolocationPermissions.Callback pendingGeoCallback;
    private String pendingGeoOrigin = "";
    private PermissionRequest pendingWebPermissionRequest;

    /*
     * true  = offline mode; load mobile files from assets/mobile
     * false = online mode; use the live production website
     */
    private volatile boolean forceBundledMobileAssets = false;

    /*
     * Prevents repeated offline fallback loops.
     */
    private boolean offlineFallbackStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        setTheme(com.saymabuilders.mobile.R.style.Theme_Sayma);
        super.onCreate(savedInstanceState);

        /*
         * Android 15 / targetSdk 35 uses edge-to-edge.
         * We explicitly handle system-bar insets below so the
         * website header does not sit underneath the notch/status bar
         * and the website bottom navigation does not sit underneath
         * the Android gesture/navigation area.
         */
        setupWindow();

        rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.WHITE);

        webView = new WebView(this);

        FrameLayout.LayoutParams webParams =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                );

        rootLayout.addView(webView, webParams);

        /*
         * Small native loading indicator.
         * It is only visible while the WebView is navigating and
         * does not change the website's actual UI/design.
         */
        loadingBar = new ProgressBar(this);
        loadingBar.setIndeterminate(true);
        loadingBar.setVisibility(View.GONE);

        FrameLayout.LayoutParams loadingParams =
                new FrameLayout.LayoutParams(
                        56,
                        56
                );
        loadingParams.gravity = android.view.Gravity.CENTER;

        rootLayout.addView(loadingBar, loadingParams);

        setContentView(rootLayout);

        applySystemBarInsets();

        configureWebView();

        /*
         * Restore an existing WebView state only when the device
         * is online. For a cold start we use the remembered-login
         * logic below.
         */
        if (savedInstanceState != null && hasValidatedInternet()) {

            forceBundledMobileAssets = false;
            webView.restoreState(savedInstanceState);

        } else {

            openCorrectStartupPage();
        }
    }

    /**
     * Configure the Android window for modern edge-to-edge devices.
     */
    private void setupWindow() {

        Window window = getWindow();

        if (Build.VERSION.SDK_INT >= 21) {
            window.setStatusBarColor(Color.WHITE);
            window.setNavigationBarColor(Color.WHITE);
        }

        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(false);
            window.setStatusBarContrastEnforced(false);
        }

        /*
         * On Android 11+ the content is allowed to receive the
         * system insets. We then apply the exact safe-area padding
         * to the WebView/root container.
         */
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        }

        int uiFlags = 0;

        if (Build.VERSION.SDK_INT >= 23) {
            uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }

        if (Build.VERSION.SDK_INT >= 26) {
            uiFlags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }

        window.getDecorView().setSystemUiVisibility(uiFlags);
    }

    /**
     * Apply top/bottom system-bar insets.
     *
     * This is the important part for:
     * - display notch
     * - status bar
     * - Android gesture/navigation bar
     */
    private void applySystemBarInsets() {

        if (rootLayout == null) {
            return;
        }

        rootLayout.setOnApplyWindowInsetsListener((view, insets) -> {

            int left = 0;
            int top = 0;
            int right = 0;
            int bottom = 0;

            if (Build.VERSION.SDK_INT >= 30) {

                android.graphics.Insets systemInsets =
                        insets.getInsets(
                                WindowInsets.Type.systemBars()
                                        | WindowInsets.Type.displayCutout()
                        );

                left = systemInsets.left;
                top = systemInsets.top;
                right = systemInsets.right;
                bottom = systemInsets.bottom;

            } else {

                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }

            /*
             * Keep the whole web application inside the physical
             * safe area of the phone.
             *
             * Header therefore starts below the notch/status bar.
             * Bottom web navigation stays above Android navigation.
             */
            view.setPadding(left, top, right, bottom);

            return insets;
        });

        rootLayout.requestApplyInsets();
    }

    /**
     * Configure WebView without changing the website's visual design.
     */
    private void configureWebView() {

        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);

        /*
         * Local bundled files are accessed only through the native
         * request interceptor. We do not expose arbitrary file:// access.
         */
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        settings.setUserAgentString(
                settings.getUserAgentString()
                        + " SAYMABuildersAndroid/1.2.0"
        );

        /*
         * Persist the PHP session cookie.
         */
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, false);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    WebResourceRequest request
            ) {

                // PDF iframe URLs are handed to the native PdfRenderer on WebView
                // versions without the JavaScript message bridge.
                if (isPdfPreviewUrl(request.getUrl())) {
                    openPdfFromUrl(request.getUrl());
                    return true;
                }
                // Let other iframe navigations load inside the current portal.
                // This keeps Plus Tools websites embedded when their CSP allows it.
                if (!request.isForMainFrame()) return false;
                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {
                Uri uri = Uri.parse(url);
                if (isPdfPreviewUrl(uri)) { openPdfFromUrl(uri); return true; }
                String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
                // The legacy callback cannot tell whether a URL belongs to an
                // iframe. Keep web pages in the app; only mail/tel are external.
                if ("https".equals(scheme) || "http".equals(scheme)) return false;
                return handleNavigation(uri);
            }

            /**
             * When offline, serve the bundled mobile website files.
             *
             * When online, return null so WebView loads the live
             * production files from saymabuilders.com.
             */
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {

                if (forceBundledMobileAssets) {
                    return interceptBundledMobileAsset(
                            request.getUrl()
                    );
                }

                return null;
            }

            @Override
            public void onPageStarted(
                    WebView view,
                    String url,
                    android.graphics.Bitmap favicon
            ) {

                loadingBar.setVisibility(View.VISIBLE);

                super.onPageStarted(view, url, favicon);
            }

            @Override
            public void onPageFinished(
                    WebView view,
                    String url
            ) {

                CookieManager.getInstance().flush();

                // The login HTML may load from cache even when its API call
                // cannot reach the server. Fall back to the last role's local
                // portal instead of leaving the app on a disconnected login form.
                if (isLoginUrl(url) && !hasValidatedInternet()
                        && hasRememberedLogin() && !forceBundledMobileAssets) {
                    forceBundledMobileAssets = true;
                    view.post(MainActivity.this::loadBundledRoleHome);
                    return;
                }

                Log.i(
                        "SAYMA_WEBVIEW",
                        "Page finished: "
                                + url
                                + " | bundledOfflineAssets="
                                + forceBundledMobileAssets
                );

                /*
                 * If the authenticated mobile area has loaded while
                 * online, remember that the user has completed the
                 * first login.
                 *
                 * No password is saved.
                 */
                if (isMobileUrl(url) && hasValidatedInternet()) {
                    rememberLogin("admin");
                } else if (isWorkerDashboardUrl(url) && hasValidatedInternet()) {
                    rememberLogin("worker");
                }

                // Keep the website navigation flush with the bottom of the WebView safe area.
                // The Android system navigation/gesture bar remains below this safe area.
                applyBottomNavigationSafeEdge(view);

                loadingBar.setVisibility(View.GONE);

                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {

                super.onReceivedError(view, request, error);

                Uri failedUri = request.getUrl();
                if (failedUri == null) return;
                String failedPath = failedUri.getPath() == null ? "" : failedUri.getPath();
                String currentPage = webView == null ? "" : String.valueOf(webView.getUrl());
                boolean portalApiFailure = failedPath.endsWith("/api.php")
                        && (isMobileUrl(currentPage) || isWorkerUrl(currentPage) || isLoginUrl(currentPage));
                if (!request.isForMainFrame() && !portalApiFailure) return;

                Log.e(
                        "SAYMA_WEBVIEW",
                        "Main frame load error "
                                + error.getErrorCode()
                                + " at "
                                + failedUri
                                + ": "
                                + error.getDescription()
                );

                /*
                 * If the live login/mobile page cannot be reached and
                 * the user has previously logged in, switch to the
                 * bundled offline mobile UI.
                 */
                if (!offlineFallbackStarted
                        && (request.isForMainFrame() || portalApiFailure)
                        && (isLoginUrl(failedUri.toString())
                        || isMobileUrl(failedUri.toString())
                        || isWorkerUrl(failedUri.toString())
                        || portalApiFailure)) {

                    offlineFallbackStarted = true;

                    if (hasRememberedLogin()) {

                        forceBundledMobileAssets = true;

                        view.post(MainActivity.this::loadBundledRoleHome);

                    } else {

                        forceBundledMobileAssets = false;

                        view.post(() ->
                                showFirstLoginOfflineMessage()
                        );
                    }
                }

                loadingBar.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedHttpError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceResponse errorResponse
            ) {

                super.onReceivedHttpError(
                        view,
                        request,
                        errorResponse
                );

                Uri errorUri = request.getUrl();
                String errorPath = errorUri == null ? null : errorUri.getPath();
                if (request.isForMainFrame()
                        || (errorPath != null && (errorPath.startsWith(MOBILE_PREFIX)
                        || errorPath.startsWith(WORKER_PREFIX)))) {

                    Log.e(
                            "SAYMA_WEBVIEW",
                            "HTTP "
                                    + errorResponse.getStatusCode()
                                    + " "
                                    + request.getUrl()
                    );
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onConsoleMessage(
                    ConsoleMessage message
            ) {

                Log.e(
                        "SAYMA_JS",
                        message.message()
                                + " -- "
                                + message.sourceId()
                                + ":"
                                + message.lineNumber()
                );

                return true;
            }

            @Override
            public boolean onJsConfirm(
                    WebView view,
                    String url,
                    String message,
                    JsResult result
            ) {

                new AlertDialog.Builder(MainActivity.this)
                        .setMessage(message)
                        .setPositiveButton(
                                "Continue",
                                (dialog, which) ->
                                        result.confirm()
                        )
                        .setNegativeButton(
                                "Cancel",
                                (dialog, which) ->
                                        result.cancel()
                        )
                        .setOnCancelListener(
                                dialog -> result.cancel()
                        )
                        .show();

                return true;
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                Uri originUri = Uri.parse(origin);
                if (!HOST.equalsIgnoreCase(originUri.getHost())) {
                    callback.invoke(origin, false, false);
                    return;
                }
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false);
                    return;
                }
                pendingGeoCallback = callback;
                pendingGeoOrigin = origin;
                requestPermissions(new String[]{
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                }, GEO_PERMISSION_REQUEST);
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                Uri origin = request.getOrigin();
                if (origin == null || !HOST.equalsIgnoreCase(origin.getHost())) {
                    request.deny();
                    return;
                }
                boolean needsCamera = false;
                boolean needsAudio = false;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) needsCamera = true;
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource)) needsAudio = true;
                }
                boolean cameraOk = !needsCamera || hasPermission(android.Manifest.permission.CAMERA);
                boolean audioOk = !needsAudio || hasPermission(android.Manifest.permission.RECORD_AUDIO);
                if (cameraOk && audioOk) {
                    request.grant(request.getResources());
                    return;
                }
                pendingWebPermissionRequest = request;
                java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
                if (needsCamera && !cameraOk) permissions.add(android.Manifest.permission.CAMERA);
                if (needsAudio && !audioOk) permissions.add(android.Manifest.permission.RECORD_AUDIO);
                requestPermissions(permissions.toArray(new String[0]), WEB_MEDIA_PERMISSION_REQUEST);
            }

            @Override
            public boolean onShowFileChooser(
                    WebView view,
                    ValueCallback<Uri[]> callback,
                    FileChooserParams params
            ) {

                if (filePathCallback != null) {
                    filePathCallback.onReceiveValue(null);
                    filePathCallback = null;
                }

                filePathCallback = callback;

                if (params.isCaptureEnabled() && !hasPermission(android.Manifest.permission.CAMERA)) {
                    pendingFileChooserParams = params;
                    requestPermissions(new String[]{android.Manifest.permission.CAMERA}, CAMERA_PICKER_PERMISSION_REQUEST);
                    return true;
                }

                try {

                    Intent intent = params.createIntent();

                    startActivityForResult(
                            intent,
                            FILE_CHOOSER_REQUEST
                    );

                    return true;

                } catch (ActivityNotFoundException ex) {

                    filePathCallback = null;

                    Toast.makeText(
                            MainActivity.this,
                            "No file picker is available.",
                            Toast.LENGTH_LONG
                    ).show();

                    return false;
                }
            }
        });

        // Authenticated PDFs and other files are downloaded with the current
        // WebView session cookie instead of launching an external viewer.
        webView.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) ->
                enqueueDownload(url, userAgent, contentDisposition, mimeType));

        // Origin-restricted native bridge. Messages are accepted only from the
        // SAYMA main frame, never from an embedded third-party tool iframe.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(
                    webView,
                    "SaymaNative",
                    Collections.singleton("https://saymabuilders.com"),
                    (view, message, sourceOrigin, isMainFrame, replyProxy) -> {
                        if (!isMainFrame || sourceOrigin == null
                                || !HOST.equalsIgnoreCase(sourceOrigin.getHost())) return;
                        handleNativeMessage(message.getData());
                    }
            );
        }
    }

    /**
     * Decide what the app should show when it starts.
     *
     * First ever launch:
     *   Online  -> existing login page
     *   Offline -> first-login-required screen
     *
     * Previously logged in:
     *   Online  -> mobile dashboard
     *   Offline -> bundled mobile dashboard
     */
    private void openCorrectStartupPage() {
        offlineFallbackStarted = false;
        if (hasValidatedInternet()) {
            forceBundledMobileAssets = false;
            // Always enter through the single role-aware login page. The PHP
            // session decides whether this is Admin Desktop/Mobile or Worker.
            webView.loadUrl(LOGIN_URL);
        } else if (hasRememberedLogin()) {
            forceBundledMobileAssets = true;
            loadBundledRoleHome();
        } else {
            forceBundledMobileAssets = false;
            showFirstLoginOfflineMessage();
        }
    }

    /**
     * Internet validation.
     *
     * This checks for a validated network rather than merely checking
     * whether Wi-Fi/mobile data is turned on.
     */
    private boolean hasValidatedInternet() {

        try {

            ConnectivityManager cm =
                    (ConnectivityManager)
                            getSystemService(
                                    CONNECTIVITY_SERVICE
                            );

            if (cm == null) {
                return false;
            }

            Network network = cm.getActiveNetwork();

            if (network == null) {
                return false;
            }

            NetworkCapabilities caps =
                    cm.getNetworkCapabilities(network);

            return caps != null
                    && caps.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_INTERNET
            )
                    && caps.hasCapability(
                    NetworkCapabilities.NET_CAPABILITY_VALIDATED
            );

        } catch (Exception ignored) {

            return false;
        }
    }

    /**
     * Remember successful first login.
     *
     * Password is NEVER stored here.
     */
    private void rememberLogin(String role) {
        String safeRole = "worker".equalsIgnoreCase(role) ? "worker" : "admin";
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_HAS_LOGGED_IN, true)
                .putString(KEY_LAST_ROLE, safeRole)
                .apply();
    }

    private void forgetLogin() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_HAS_LOGGED_IN, false)
                .remove(KEY_LAST_ROLE)
                .apply();
    }

    private boolean hasRememberedLogin() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(KEY_HAS_LOGGED_IN, false);
    }

    private String lastRole() {
        return getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getString(KEY_LAST_ROLE, "admin");
    }

    private boolean isMobileUrl(String url) {

        try {

            Uri uri = Uri.parse(url);

            String host =
                    uri.getHost() == null
                            ? ""
                            : uri.getHost()
                            .toLowerCase(Locale.ROOT);

            String path =
                    uri.getPath() == null
                            ? ""
                            : uri.getPath();

            return HOST.equals(host)
                    && (path.equals(MOBILE_PREFIX)
                    || path.startsWith(
                    MOBILE_PREFIX + "/"
            ));

        } catch (Exception ignored) {

            return false;
        }
    }

    private boolean isWorkerDashboardUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            return HOST.equals(host) && (path.equals(WORKER_PREFIX)
                    || path.equals(WORKER_PREFIX + "/")
                    || path.equals(WORKER_PREFIX + "/index.html"));
        } catch (Exception ignored) { return false; }
    }

    private boolean isWorkerUrl(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            String path = uri.getPath() == null ? "" : uri.getPath();
            return HOST.equals(host) && (path.equals(WORKER_PREFIX) || path.startsWith(WORKER_PREFIX + "/"));
        } catch (Exception ignored) { return false; }
    }

    private boolean isLoginUrl(String url) {

        try {

            Uri uri = Uri.parse(url);

            String host =
                    uri.getHost() == null
                            ? ""
                            : uri.getHost()
                            .toLowerCase(Locale.ROOT);

            String path =
                    uri.getPath() == null
                            ? ""
                            : uri.getPath();

            return HOST.equals(host)
                    && path.toLowerCase(Locale.ROOT)
                    .contains("/login");

        } catch (Exception ignored) {

            return false;
        }
    }

    /**
     * Send the same online/offline event to the existing web app.
     */
    @Override
    protected void onResume() {

        super.onResume();

        if (webView != null) {

            webView.postDelayed(
                    () -> {

                        if (webView == null) {
                            return;
                        }

                        String event =
                                hasValidatedInternet()
                                        ? "online"
                                        : "offline";

                        webView.evaluateJavascript(
                                "window.dispatchEvent(new Event('"
                                        + event
                                        + "'));",
                                null
                        );

                    },
                    350
            );
        }
    }

    /**
     * Navigation policy.
     *
     * The existing SAYMA Builders domain remains inside the app.
     * External links are opened by Android/browser.
     */
    private boolean handleNavigation(Uri uri) {

        String scheme =
                uri.getScheme() == null
                        ? ""
                        : uri.getScheme()
                        .toLowerCase(Locale.ROOT);

        String host =
                uri.getHost() == null
                        ? ""
                        : uri.getHost()
                        .toLowerCase(Locale.ROOT);

        if ("https".equals(scheme)
                && HOST.equals(host)) {

            return false;
        }

        if ("http".equals(scheme)
                && HOST.equals(host)) {

            Toast.makeText(
                    this,
                    "Please use the secure HTTPS site.",
                    Toast.LENGTH_SHORT
            ).show();

            return true;
        }

        if ("mailto".equals(scheme)
                || "tel".equals(scheme)) {

            try {

                startActivity(
                        new Intent(
                                Intent.ACTION_VIEW,
                                uri
                        )
                );

            } catch (Exception ignored) {
            }

            return true;
        }

        if ("https".equals(scheme)
                || "http".equals(scheme)) {

            try {

                startActivity(
                        new Intent(
                                Intent.ACTION_VIEW,
                                uri
                        )
                );

            } catch (Exception ignored) {
            }

            return true;
        }

        return true;
    }

    /**
     * Serve the bundled mobile website only while offline.
     *
     * Expected asset structure:
     *
     * app/src/main/assets/mobile/
     *     index.html
     *     ...
     */
    private WebResourceResponse interceptBundledMobileAsset(Uri uri) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!HOST.equals(host)) return null;
        String path = uri.getPath() == null ? "" : uri.getPath();
        String assetBase;
        String relative;

        if (path.equals(MOBILE_PREFIX) || path.startsWith(MOBILE_PREFIX + "/")) {
            assetBase = "mobile/";
            relative = (path.equals(MOBILE_PREFIX) || path.equals(MOBILE_PREFIX + "/"))
                    ? "index.html" : path.substring((MOBILE_PREFIX + "/").length());
        } else if (path.equals(WORKER_PREFIX) || path.startsWith(WORKER_PREFIX + "/")) {
            assetBase = "worker/";
            relative = (path.equals(WORKER_PREFIX) || path.equals(WORKER_PREFIX + "/"))
                    ? "index.html" : path.substring((WORKER_PREFIX + "/").length());
        } else if (path.equals(PORTAL_PREFIX + "/manifest.webmanifest")) {
            assetBase = "";
            relative = "manifest.webmanifest";
        } else {
            return null;
        }

        if (relative.contains("..") || relative.startsWith("/")) return null;
        try {
            InputStream stream = getAssets().open(assetBase + relative);
            String mime = mimeType(relative);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-store");
            headers.put("X-Content-Type-Options", "nosniff");
            return new WebResourceResponse(mime, "UTF-8", 200, "OK", headers, stream);
        } catch (IOException ignored) {
            return null;
        }
    }

    private String mimeType(String file) {

        String lower =
                file.toLowerCase(Locale.ROOT);

        if (lower.endsWith(".html")) {
            return "text/html";
        }

        if (lower.endsWith(".js")) {
            return "application/javascript";
        }

        if (lower.endsWith(".css")) {
            return "text/css";
        }

        if (lower.endsWith(".json")) {
            return "application/json";
        }

        if (lower.endsWith(".webmanifest")) {
            return "application/manifest+json";
        }

        if (lower.endsWith(".png")) {
            return "image/png";
        }

        if (lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")) {

            return "image/jpeg";
        }

        if (lower.endsWith(".webp")) {
            return "image/webp";
        }

        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }

        if (lower.endsWith(".woff2")) {
            return "font/woff2";
        }

        if (lower.endsWith(".woff")) {
            return "font/woff";
        }

        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }

        if (lower.endsWith(".ico")) {
            return "image/x-icon";
        }

        return "application/octet-stream";
    }

    private boolean hasPermission(String permission) {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        // The attendance geofence needs precise GPS, not approximate location.
        return hasPermission(android.Manifest.permission.ACCESS_FINE_LOCATION);
    }

    private void launchPendingFileChooser() {
        if (pendingFileChooserParams == null || filePathCallback == null) return;
        try {
            Intent picker = pendingFileChooserParams.createIntent();
            if (pendingFileChooserParams.isCaptureEnabled()
                    && Build.VERSION.SDK_INT >= 29
                    && (picker.getType() == null || picker.getType().startsWith("image/"))) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "SAYMA-entry-" + System.currentTimeMillis() + ".jpg");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/SAYMA BUILDERS");
                values.put(MediaStore.Images.Media.IS_PENDING, 1);
                pendingCameraImageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (pendingCameraImageUri != null) {
                    Intent capture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    capture.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraImageUri);
                    capture.setClipData(ClipData.newUri(getContentResolver(), "SAYMA attendance photo", pendingCameraImageUri));
                    capture.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    Intent chooser = Intent.createChooser(picker, "Choose a photo or take a new one");
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{capture});
                    startActivityForResult(chooser, FILE_CHOOSER_REQUEST);
                } else {
                    startActivityForResult(picker, FILE_CHOOSER_REQUEST);
                }
            } else {
                startActivityForResult(picker, FILE_CHOOSER_REQUEST);
            }
        } catch (Exception e) {
            if (pendingCameraImageUri != null) { try { getContentResolver().delete(pendingCameraImageUri, null, null); } catch (Exception ignored) {} pendingCameraImageUri = null; }
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = null;
            Toast.makeText(this, "Could not open the file or camera picker.", Toast.LENGTH_LONG).show();
        } finally {
            pendingFileChooserParams = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == GEO_PERMISSION_REQUEST) {
            boolean locationOk = hasLocationPermission();
            if (pendingGeoCallback != null) {
                pendingGeoCallback.invoke(pendingGeoOrigin, locationOk, false);
                pendingGeoCallback = null; pendingGeoOrigin = "";
            }
            if (!locationOk) Toast.makeText(this, "Allow precise (not approximate) location for verified attendance.", Toast.LENGTH_LONG).show();
        } else if (requestCode == WEB_MEDIA_PERMISSION_REQUEST) {
            PermissionRequest request = pendingWebPermissionRequest; pendingWebPermissionRequest = null;
            if (request != null) {
                boolean ok = true;
                for (String resource : request.getResources()) {
                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource) && !hasPermission(android.Manifest.permission.CAMERA)) ok = false;
                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(resource) && !hasPermission(android.Manifest.permission.RECORD_AUDIO)) ok = false;
                }
                if (ok) request.grant(request.getResources()); else request.deny();
            }
        } else if (requestCode == CAMERA_PICKER_PERMISSION_REQUEST) {
            if (granted && hasPermission(android.Manifest.permission.CAMERA)) launchPendingFileChooser();
            else {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = null; pendingFileChooserParams = null;
                Toast.makeText(this, "Camera permission is required to capture the attendance photo.", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void handleNativeMessage(String raw) {
        try {
            JSONObject data = new JSONObject(raw == null ? "{}" : raw);
            String action = data.optString("action", "");
            if ("clearRememberedLogin".equals(action)) { forgetLogin(); return; }
            if ("rememberRole".equals(action)) { rememberLogin(data.optString("role", "admin")); return; }
            if ("openPdfBase64".equals(action)) {
                String base64 = data.optString("data", "");
                String name = data.optString("filename", "SAYMA-document.pdf");
                if (base64.length() > 32 * 1024 * 1024) throw new IllegalArgumentException("PDF is too large for the in-app viewer.");
                byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
                openPdfBytes(bytes, name);
                return;
            }
            if ("downloadFile".equals(action)) {
                enqueueSecureDocumentDownload(data.optString("url", ""), data.optString("filename", "document"));
                return;
            }
            if ("saveBase64File".equals(action)) {
                String base64 = data.optString("data", "");
                if (base64.length() > 32 * 1024 * 1024) throw new IllegalArgumentException("File is too large to save.");
                saveBytesToDownloads(Base64.decode(base64, Base64.DEFAULT), data.optString("filename", "document"), data.optString("mimeType", "application/octet-stream"));
            }
        } catch (Exception e) {
            Log.e("SAYMA_NATIVE", "Native action failed", e);
            runOnUiThread(() -> Toast.makeText(this, e.getMessage() == null ? "Could not complete the native action." : e.getMessage(), Toast.LENGTH_LONG).show());
        }
    }

    private boolean isAllowedPortalFileUrl(String raw) {
        try {
            Uri uri = Uri.parse(raw);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !HOST.equalsIgnoreCase(uri.getHost())) return false;
            if (!"/portal/api.php".equals(uri.getPath())) return false;
            String action = uri.getQueryParameter("action");
            return action != null && java.util.Arrays.asList(
                    "view_document", "view_worker_document", "view_project_document", "view_claim_pdf",
                    "worker_view_salary_slip", "admin_view_worker_salary_slip", "worker_view_payment_receipt",
                    "worker_view_leave_mc", "admin_view_worker_leave_mc", "worker_view_attendance_proof",
                    "admin_view_worker_attendance_proof"
            ).contains(action);
        } catch (Exception ignored) { return false; }
    }

    private boolean isPdfPreviewUrl(Uri uri) {
        return uri != null
                && "pdf".equalsIgnoreCase(uri.getQueryParameter("preview"))
                && isAllowedPortalFileUrl(uri.toString());
    }

    private void openPdfFromUrl(Uri uri) {
        if (uri == null || !isPdfPreviewUrl(uri)) return;
        new Thread(() -> {
            HttpURLConnection connection = null;
            File file = null;
            try {
                URL url = new URL(uri.toString());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(45000);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/pdf");
                if (webView != null) connection.setRequestProperty("User-Agent", webView.getSettings().getUserAgentString());
                String cookie = CookieManager.getInstance().getCookie(uri.toString());
                if (cookie != null && !cookie.isEmpty()) connection.setRequestProperty("Cookie", cookie);
                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) throw new IOException("PDF request failed (HTTP " + status + ").");
                String contentType = connection.getContentType();
                if (contentType != null) {
                    String normalizedType = contentType.toLowerCase(Locale.ROOT);
                    if (normalizedType.contains("application/json") || normalizedType.contains("text/html") || normalizedType.contains("text/plain")) {
                        throw new IOException("The server did not return a PDF. Please sign in again and retry.");
                    }
                }
                int contentLength = connection.getContentLength();
                if (contentLength > 24 * 1024 * 1024) throw new IOException("PDF exceeds the 24 MB in-app preview limit.");
                file = File.createTempFile("sayma-preview-", ".pdf", getCacheDir());
                try (InputStream input = connection.getInputStream(); FileOutputStream output = new FileOutputStream(file)) {
                    byte[] buffer = new byte[16 * 1024];
                    int count; long total = 0;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > 24L * 1024L * 1024L) throw new IOException("PDF exceeds the 24 MB in-app preview limit.");
                        output.write(buffer, 0, count);
                    }
                }
                File ready = file;
                String name = uri.getQueryParameter("filename");
                if (name == null || name.trim().isEmpty()) name = "SAYMA-document.pdf";
                String finalName = safeFilename(name);
                runOnUiThread(() -> {
                    Intent intent = new Intent(MainActivity.this, PdfViewerActivity.class);
                    intent.putExtra(PdfViewerActivity.EXTRA_FILE_PATH, ready.getAbsolutePath());
                    intent.putExtra(PdfViewerActivity.EXTRA_FILE_NAME, finalName);
                    startActivity(intent);
                });
            } catch (Exception e) {
                if (file != null && file.exists()) { try { file.delete(); } catch (Exception ignored) {} }
                Log.e("SAYMA_PDF", "Could not open PDF from server", e);
                runOnUiThread(() -> Toast.makeText(this, e.getMessage() == null ? "Could not open PDF." : e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (connection != null) connection.disconnect();
            }
        }).start();
    }

    private void enqueueSecureDocumentDownload(String rawUrl, String filename) {
        if (!isAllowedPortalFileUrl(rawUrl)) throw new IllegalArgumentException("This download URL is not an approved SAYMA document endpoint.");
        enqueueDownload(rawUrl, webView == null ? "" : webView.getSettings().getUserAgentString(), null, null, filename);
    }

    private void enqueueDownload(String url, String userAgent, String contentDisposition, String mimeType) {
        enqueueDownload(url, userAgent, contentDisposition, mimeType, null);
    }

    private void enqueueDownload(String url, String userAgent, String contentDisposition, String mimeType, String requestedName) {
        try {
            Uri uri = Uri.parse(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) throw new IllegalArgumentException("Downloads require HTTPS.");
            DownloadManager manager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (manager == null) throw new IllegalStateException("Android Download Manager is unavailable.");
            String filename = requestedName == null || requestedName.trim().isEmpty()
                    ? URLUtil.guessFileName(url, contentDisposition, mimeType)
                    : requestedName;
            filename = safeFilename(filename);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setTitle(filename);
            request.setDescription("SAYMA BUILDERS document download");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename);
            if (mimeType != null && !mimeType.trim().isEmpty()) request.setMimeType(mimeType);
            if (userAgent != null && !userAgent.isEmpty()) request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null && !cookie.isEmpty()) request.addRequestHeader("Cookie", cookie);
            request.addRequestHeader("Accept", "application/pdf,image/*,*/*;q=0.8");
            manager.enqueue(request);
            Toast.makeText(this, "Download started · check your Downloads folder.", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, e.getMessage() == null ? "Download failed." : e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String safeFilename(String name) {
        String clean = name == null ? "document" : name.replaceAll("[\\/:*?\"<>|\r\n]", "_").trim();
        if (clean.isEmpty()) clean = "document";
        return clean.length() > 150 ? clean.substring(clean.length() - 150) : clean;
    }

    private void saveBytesToDownloads(byte[] bytes, String filename, String mimeType) throws Exception {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("The file is empty.");
        if (bytes.length > 24 * 1024 * 1024) throw new IllegalArgumentException("File exceeds the 24 MB in-app save limit.");
        String safe = safeFilename(filename);
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, safe);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType == null ? "application/octet-stream" : mimeType);
            values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SAYMA BUILDERS");
            Uri target = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (target == null) throw new IllegalStateException("Could not create a Downloads file.");
            try (OutputStream output = getContentResolver().openOutputStream(target)) { if (output == null) throw new IllegalStateException("Could not write the Downloads file."); output.write(bytes); }
        } else {
            File folder = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (folder == null) throw new IllegalStateException("Downloads folder is unavailable.");
            if (!folder.exists() && !folder.mkdirs()) throw new IllegalStateException("Could not create the Downloads folder.");
            try (FileOutputStream output = new FileOutputStream(new File(folder, safe))) { output.write(bytes); }
        }
        runOnUiThread(() -> Toast.makeText(this, "Saved to Downloads: " + safe, Toast.LENGTH_LONG).show());
    }

    private void openPdfBytes(byte[] bytes, String filename) throws Exception {
        if (bytes == null || bytes.length < 5 || bytes.length > 24 * 1024 * 1024) throw new IllegalArgumentException("PDF is empty or exceeds the 24 MB preview limit.");
        File file = File.createTempFile("sayma-preview-", ".pdf", getCacheDir());
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(bytes); }
        Intent intent = new Intent(this, PdfViewerActivity.class);
        intent.putExtra(PdfViewerActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
        intent.putExtra(PdfViewerActivity.EXTRA_FILE_NAME, safeFilename(filename));
        runOnUiThread(() -> startActivity(intent));
    }

    /**
     * File picker result.
     */
    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {

        super.onActivityResult(
                requestCode,
                resultCode,
                data
        );

        if (requestCode == FILE_CHOOSER_REQUEST && filePathCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK) {
                if (data != null && data.getData() != null) {
                    results = new Uri[]{data.getData()};
                    if (pendingCameraImageUri != null) {
                        try { getContentResolver().delete(pendingCameraImageUri, null, null); } catch (Exception ignored) {}
                        pendingCameraImageUri = null;
                    }
                } else if (pendingCameraImageUri != null) {
                    ContentValues ready = new ContentValues();
                    ready.put(MediaStore.Images.Media.IS_PENDING, 0);
                    try { getContentResolver().update(pendingCameraImageUri, ready, null, null); } catch (Exception ignored) {}
                    results = new Uri[]{pendingCameraImageUri};
                    pendingCameraImageUri = null;
                } else {
                    results = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
                }
            } else if (pendingCameraImageUri != null) {
                try { getContentResolver().delete(pendingCameraImageUri, null, null); } catch (Exception ignored) {}
                pendingCameraImageUri = null;
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
            pendingFileChooserParams = null;
        }
    }

    @Override
    protected void onSaveInstanceState(
            Bundle outState
    ) {

        if (webView != null) {
            webView.saveState(outState);
        }

        super.onSaveInstanceState(outState);
    }

    /**
     * Normal Android back button:
     * go back inside the website first.
     */
    @Override
    public void onBackPressed() {

        if (webView != null
                && webView.canGoBack()) {

            webView.goBack();

        } else {

            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        if (filePathCallback != null) {
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
        if (pendingCameraImageUri != null) { try { getContentResolver().delete(pendingCameraImageUri, null, null); } catch (Exception ignored) {} pendingCameraImageUri = null; }

        if (pendingGeoCallback != null) { pendingGeoCallback.invoke(pendingGeoOrigin, false, false); pendingGeoCallback = null; }
        if (pendingWebPermissionRequest != null) { pendingWebPermissionRequest.deny(); pendingWebPermissionRequest = null; }
        if (webView != null) {

            webView.stopLoading();
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }

    /**
     * If the Activity regains focus after a file picker/browser,
     * re-apply the system-bar insets.
     */
    @Override
    public void onWindowFocusChanged(
            boolean hasFocus
    ) {

        super.onWindowFocusChanged(hasFocus);

        if (hasFocus && rootLayout != null) {
            rootLayout.requestApplyInsets();
        }
    }

    /**
     * Offline first-login message.
     *
     * This only appears when:
     * - there is no validated Internet connection, AND
     * - the user has never completed the first online login.
     */
    /**
     * Load the bundled mobile index directly from APK assets.
     *
     * This avoids depending on a network request for the initial
     * offline HTML document. Relative CSS/JS/images continue to use
     * the https://saymabuilders.com/portal/mobile/ base URL, and the
     * request interceptor serves those files from assets/mobile.
     */
    private void loadBundledRoleHome() {
        if (webView == null) return;
        forceBundledMobileAssets = true;
        offlineFallbackStarted = true;
        String role = lastRole();
        String destination = "worker".equalsIgnoreCase(role) ? WORKER_URL : MOBILE_URL;
        Log.i("SAYMA_WEBVIEW", "Opening bundled offline " + role + " portal at " + destination);
        webView.loadUrl(destination);
    }

    private void applyBottomNavigationSafeEdge(WebView view) {

        if (view == null) {
            return;
        }

        String script =
                "(function(){" +
                "try{" +
                "var id='sayma-native-bottom-safe-edge';" +
                "var s=document.getElementById(id);" +
                "if(!s){s=document.createElement('style');s.id=id;" +
                "(document.head||document.documentElement).appendChild(s);}" +
                /*
                 * Keep the existing bottom position and width, but make the
                 * capsule visibly slimmer. The previous 77px container and
                 * 58px tab buttons left too much vertical space around the
                 * icons and labels on compact phone screens.
                 */
                "s.textContent=" +
                "'#bottomNav.bottom-nav,.bottom-nav#bottomNav{" +
                "position:fixed!important;bottom:0px!important;" +
                "height:62px!important;min-height:62px!important;max-height:62px!important;" +
                "box-sizing:border-box!important;padding:2px 5px!important;" +
                "border-width:1px!important;border-style:solid!important;" +
                "border-radius:21px!important;" +
                "box-shadow:0 8px 22px rgba(20,22,27,.18)!important;" +
                "}'+" +
                "'#bottomNav.bottom-nav button.nav-item{" +
                "height:56px!important;min-height:56px!important;max-height:56px!important;" +
                "box-sizing:border-box!important;padding:2px 1px!important;" +
                "gap:1px!important;border-radius:15px!important;" +
                "transform:none!important;margin:0!important;" +
                "}'+" +
                "'#bottomNav.bottom-nav button.nav-item .nav-icon{" +
                "height:25px!important;width:30px!important;min-height:25px!important;" +
                "border-radius:10px!important;margin:0!important;" +
                "transform:none!important;" +
                "}'+" +
                "'#bottomNav.bottom-nav button.nav-item small{" +
                "line-height:11px!important;margin:0!important;padding:0!important;" +
                "font-size:9px!important;" +
                "}'+" +
                "'#bottomNav.bottom-nav button.nav-item.chosen{" +
                "transform:none!important;box-shadow:0 3px 9px rgba(240,125,61,.22)!important;" +
                "}'+" +
                "'#bottomNav.bottom-nav button.nav-item.chosen .nav-icon{" +
                "transform:none!important;" +
                "}';" +
                "s.textContent += '.worker-dashboard-shell .worker-nav{bottom:2px!important;}';" +
                "var n=document.getElementById('bottomNav');" +
                "if(n){n.style.setProperty('bottom','0px','important');" +
                "n.style.setProperty('height','62px','important');" +
                "n.style.setProperty('min-height','62px','important');" +
                "n.style.setProperty('padding','2px 5px','important');}" +
                "}catch(e){console.error('SAYMA native nav compact fix',e);}" +
                "})();";

        view.evaluateJavascript(script, null);
    }

    private void showFirstLoginOfflineMessage() {

        String html =
                "<!doctype html>"
                        + "<html>"
                        + "<head>"
                        + "<meta name='viewport' "
                        + "content='width=device-width,"
                        + "initial-scale=1,"
                        + "maximum-scale=1,"
                        + "user-scalable=no'>"
                        + "<style>"
                        + "*{box-sizing:border-box;}"
                        + "html,body{"
                        + "margin:0;"
                        + "width:100%;"
                        + "min-height:100%;"
                        + "font-family:Arial,sans-serif;"
                        + "background:#f7f8f7;"
                        + "color:#26352f;"
                        + "}"
                        + "body{"
                        + "min-height:100vh;"
                        + "display:flex;"
                        + "align-items:center;"
                        + "justify-content:center;"
                        + "padding:28px;"
                        + "}"
                        + ".card{"
                        + "width:100%;"
                        + "max-width:420px;"
                        + "background:#fff;"
                        + "border-radius:28px;"
                        + "padding:32px 24px;"
                        + "text-align:center;"
                        + "box-shadow:0 12px 40px "
                        + "rgba(0,0,0,.08);"
                        + "}"
                        + ".icon{"
                        + "font-size:52px;"
                        + "margin-bottom:16px;"
                        + "}"
                        + "h1{"
                        + "font-size:24px;"
                        + "line-height:1.25;"
                        + "margin:0 0 12px;"
                        + "}"
                        + "p{"
                        + "font-size:15px;"
                        + "line-height:1.6;"
                        + "color:#68736f;"
                        + "margin:0;"
                        + "}"
                        + ".btn{"
                        + "display:inline-block;"
                        + "margin-top:22px;"
                        + "background:#ef8b45;"
                        + "color:#fff;"
                        + "padding:14px 24px;"
                        + "border-radius:16px;"
                        + "font-weight:700;"
                        + "text-decoration:none;"
                        + "}"
                        + "</style>"
                        + "</head>"
                        + "<body>"
                        + "<div class='card'>"
                        + "<div class='icon'>📡</div>"
                        + "<h1>"
                        + "Internet connection required"
                        + "</h1>"
                        + "<p>"
                        + "Please connect to the internet "
                        + "and sign in once. After your first "
                        + "successful login, SAYMA BUILDERS "
                        + "can open your saved dashboard "
                        + "while offline."
                        + "</p>"
                        + "<a class='btn' "
                        + "href='"
                        + LOGIN_URL
                        + "'>"
                        + "Open Login"
                        + "</a>"
                        + "</div>"
                        + "</body>"
                        + "</html>";

        webView.loadDataWithBaseURL(
                LOGIN_URL,
                html,
                "text/html",
                "UTF-8",
                LOGIN_URL
        );

        loadingBar.setVisibility(View.GONE);
    }
}
