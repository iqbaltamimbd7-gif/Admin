package com.saymabuilders.mobile;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final String HOST = "saymabuilders.com";
    private static final String LOGIN_URL =
            "https://saymabuilders.com/portal/login.html?next=mobile";
    private static final String MOBILE_URL =
            "https://saymabuilders.com/portal/mobile/";
    private static final String MOBILE_PREFIX = "/portal/mobile";

    private static final int FILE_CHOOSER_REQUEST = 5107;

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

    private WebView webView;
    private FrameLayout rootLayout;
    private ProgressBar loadingBar;
    private ValueCallback<Uri[]> filePathCallback;

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

        /*
         * Local bundled files are accessed only through the native
         * request interceptor. We do not expose arbitrary file:// access.
         */
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true);

        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMixedContentMode(
                WebSettings.MIXED_CONTENT_NEVER_ALLOW
        );

        settings.setUserAgentString(
                settings.getUserAgentString()
                        + " SAYMABuildersAndroid/1.1"
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

                return handleNavigation(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view,
                    String url
            ) {

                return handleNavigation(Uri.parse(url));
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
                if (isMobileUrl(url)
                        && hasValidatedInternet()) {

                    rememberLogin();
                }

                /*
                 * If the server sends an authenticated user back to
                 * login, the online PHP session is no longer valid.
                 */
                if (isLoginUrl(url)
                        && hasValidatedInternet()) {

                    forgetLogin();
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

                if (!request.isForMainFrame()) {
                    return;
                }

                Uri failedUri = request.getUrl();

                if (failedUri == null) {
                    return;
                }

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
                        && (isLoginUrl(failedUri.toString())
                        || isMobileUrl(failedUri.toString()))) {

                    offlineFallbackStarted = true;

                    if (hasRememberedLogin()) {

                        forceBundledMobileAssets = true;

                        view.post(MainActivity.this::loadBundledMobileHome);

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

                if (request.isForMainFrame()
                        || (request.getUrl() != null
                        && request.getUrl().getPath() != null
                        && request.getUrl().getPath()
                        .startsWith(MOBILE_PREFIX))) {

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

        /*
         * Keep the existing file download behaviour.
         */
        webView.setDownloadListener(
                (url,
                 userAgent,
                 contentDisposition,
                 mimeType,
                 contentLength) -> {

                    try {

                        Intent intent = new Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse(url)
                        );

                        startActivity(intent);

                    } catch (Exception ignored) {

                        Toast.makeText(
                                MainActivity.this,
                                "This download cannot be opened here.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
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

        boolean online = hasValidatedInternet();
        boolean remembered = hasRememberedLogin();

        if (online) {

            forceBundledMobileAssets = false;

            if (remembered) {

                /*
                 * The PHP session cookie should already be persisted.
                 * Open the existing mobile dashboard directly.
                 *
                 * If the server session expired, the server can send
                 * the user to the normal login page.
                 */
                webView.loadUrl(MOBILE_URL);

            } else {

                /*
                 * First login remains exactly the existing login page.
                 */
                webView.loadUrl(LOGIN_URL);
            }

        } else {

            if (remembered) {

                /*
                 * Previously authenticated user:
                 * use the bundled mobile UI and its persisted
                 * WebView storage/cache while offline.
                 */
                forceBundledMobileAssets = true;
                loadBundledMobileHome();

            } else {

                /*
                 * Never logged in before + no Internet:
                 * do not show a blank white screen.
                 */
                forceBundledMobileAssets = false;
                showFirstLoginOfflineMessage();
            }
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
    private void rememberLogin() {

        getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        )
                .edit()
                .putBoolean(
                        KEY_HAS_LOGGED_IN,
                        true
                )
                .apply();
    }

    private void forgetLogin() {

        getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        )
                .edit()
                .putBoolean(
                        KEY_HAS_LOGGED_IN,
                        false
                )
                .apply();
    }

    private boolean hasRememberedLogin() {

        return getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
        )
                .getBoolean(
                        KEY_HAS_LOGGED_IN,
                        false
                );
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
    private WebResourceResponse interceptBundledMobileAsset(
            Uri uri
    ) {

        String host =
                uri.getHost() == null
                        ? ""
                        : uri.getHost()
                        .toLowerCase(Locale.ROOT);

        if (!HOST.equals(host)) {
            return null;
        }

        String path =
                uri.getPath() == null
                        ? ""
                        : uri.getPath();

        if (!(path.equals(MOBILE_PREFIX)
                || path.startsWith(
                MOBILE_PREFIX + "/"
        ))) {

            return null;
        }

        String relative;

        if (path.equals(MOBILE_PREFIX)
                || path.equals(MOBILE_PREFIX + "/")) {

            relative = "index.html";

        } else {

            relative =
                    path.substring(
                            (MOBILE_PREFIX + "/").length()
                    );
        }

        /*
         * Prevent path traversal.
         */
        if (relative.contains("..")
                || relative.startsWith("/")) {

            return null;
        }

        try {

            InputStream stream =
                    getAssets().open(
                            "mobile/" + relative
                    );

            String mime =
                    mimeType(relative);

            Map<String, String> headers =
                    new HashMap<>();

            headers.put(
                    "Cache-Control",
                    "no-store"
            );

            headers.put(
                    "X-Content-Type-Options",
                    "nosniff"
            );

            return new WebResourceResponse(
                    mime,
                    "UTF-8",
                    200,
                    "OK",
                    headers,
                    stream
            );

        } catch (IOException ignored) {

            /*
             * If a local asset does not exist, return null.
             * WebView can then attempt the normal server request.
             */
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

        if (requestCode == FILE_CHOOSER_REQUEST
                && filePathCallback != null) {

            Uri[] results =
                    WebChromeClient.FileChooserParams
                            .parseResult(
                                    resultCode,
                                    data
                            );

            filePathCallback.onReceiveValue(
                    results
            );

            filePathCallback = null;
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
    private void loadBundledMobileHome() {

        if (webView == null) {
            return;
        }

        forceBundledMobileAssets = true;
        offlineFallbackStarted = true;

        Log.i(
                "SAYMA_WEBVIEW",
                "Opening bundled offline app at the original HTTPS origin: " + MOBILE_URL
        );

        /*
         * Important: navigate to the real HTTPS URL and intercept the main document
         * plus its JS/CSS/assets in shouldInterceptRequest().
         * This keeps IndexedDB, localStorage and cookies on the same origin as the
         * live website. Loading HTML with loadDataWithBaseURL can create a different
         * WebView document context on some Android WebView versions.
         */
        webView.loadUrl(MOBILE_URL);
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
