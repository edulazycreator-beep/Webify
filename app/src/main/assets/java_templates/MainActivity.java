package {{PACKAGE}};

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * Activity utama aplikasi hasil Webify. Hanya memakai API bawaan android.jar.
 */
public class MainActivity extends Activity {

    private static final String TAG = "WebifyApp";
    static final int REQ_FILE_CHOOSER = 4101;
    static final int REQ_PERMISSIONS_WEB = 4102;
    static final int REQ_PERMISSIONS_GEO = 4103;
    static final int REQ_PERMISSIONS_FILE = 4104;
    static final int REQ_PERMISSIONS_NOTIF = 4105;
    static final int REQ_PERMISSIONS_STORAGE = 4106;

    private LinearLayout rootColumn;
    private View statusSpacer;
    private View navSpacer;
    private FrameLayout contentFrame;
    private WebifyRefreshLayout refreshLayout;
    WebView webView;
    private ProgressBar topProgress;
    private WebifySplashView splashView;
    private WebifyWebViewClient webViewClient;
    private WebifyWebChromeClient chromeClient;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean splashDismissed = false;
    private boolean minSplashElapsed = false;
    private boolean firstPageLoaded = false;
    private String lastFailedUrl = null;
    boolean showingOffline = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindow();
        buildLayout();
        setupWebView();
        setupSplash();

        if (savedInstanceState != null && webView.restoreState(savedInstanceState) != null) {
            dismissSplashNow();
        } else {
            loadInitial(getIntent());
        }
        if (BuildConfig.PERM_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_PERMISSIONS_NOTIF);
        }
    }

    // ------------------------------------------------------------------ Window & layout
    private void setupWindow() {
        Window w = getWindow();
        if (BuildConfig.KEEP_SCREEN_ON) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (BuildConfig.PREVENT_SCREENSHOTS) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) {
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        // Edge-to-edge: konten di belakang system bar, warna bar digambar oleh spacer.
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!BuildConfig.STATUS_BAR_LIGHT_ICONS) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            w.getDecorView().setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.layoutInDisplayCutoutMode = BuildConfig.FULLSCREEN
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT;
            w.setAttributes(lp);
        }
    }

    private void applySystemBarAppearance() {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = w.getInsetsController();
            if (c == null) return;
            int light = 0;
            if (!BuildConfig.STATUS_BAR_LIGHT_ICONS) light |= WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
            if (isLightColor(BuildConfig.NAV_BAR_COLOR)) light |= WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(light, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            if (BuildConfig.FULLSCREEN) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.systemBars());
            }
        } else {
            View decor = w.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!BuildConfig.STATUS_BAR_LIGHT_ICONS) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26 && isLightColor(BuildConfig.NAV_BAR_COLOR)) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            if (BuildConfig.FULLSCREEN) {
                flags |= View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BuildConfig.NAV_BAR_COLOR);

        rootColumn = new LinearLayout(this);
        rootColumn.setOrientation(LinearLayout.VERTICAL);
        root.addView(rootColumn, new FrameLayout.LayoutParams(-1, -1));

        statusSpacer = new View(this);
        statusSpacer.setBackgroundColor(BuildConfig.STATUS_BAR_COLOR);
        rootColumn.addView(statusSpacer, new LinearLayout.LayoutParams(-1, 0));

        contentFrame = new FrameLayout(this);
        contentFrame.setBackgroundColor(Color.WHITE);
        rootColumn.addView(contentFrame, new LinearLayout.LayoutParams(-1, 0, 1f));

        navSpacer = new View(this);
        navSpacer.setBackgroundColor(BuildConfig.NAV_BAR_COLOR);
        rootColumn.addView(navSpacer, new LinearLayout.LayoutParams(-1, 0));

        webView = new WebView(this);
        refreshLayout = new WebifyRefreshLayout(this, webView, BuildConfig.PROGRESS_COLOR);
        refreshLayout.setEnabledPull(BuildConfig.ENABLE_PULL_TO_REFRESH);
        refreshLayout.setOnRefreshListener(new WebifyRefreshLayout.OnRefreshListener() {
            @Override public void onRefresh() { reload(); }
        });
        contentFrame.addView(refreshLayout, new FrameLayout.LayoutParams(-1, -1));

        topProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        topProgress.setMax(100);
        topProgress.setIndeterminate(false);
        topProgress.setProgressTintList(ColorStateList.valueOf(BuildConfig.PROGRESS_COLOR));
        topProgress.setProgressBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(-1, dp(3));
        plp.topMargin = -dp(1);
        contentFrame.addView(topProgress, plp);
        topProgress.setVisibility(View.GONE);

        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top, bottom, left, right;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    android.graphics.Insets ime = insets.getInsets(WindowInsets.Type.ime());
                    top = bars.top; left = bars.left; right = bars.right;
                    bottom = Math.max(bars.bottom, ime.bottom);
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                    left = insets.getSystemWindowInsetLeft();
                    right = insets.getSystemWindowInsetRight();
                }
                if (BuildConfig.FULLSCREEN) { top = 0; bottom = Build.VERSION.SDK_INT >= 30 ? insets.getInsets(WindowInsets.Type.ime()).bottom : 0; }
                setHeight(statusSpacer, top);
                setHeight(navSpacer, bottom);
                rootColumn.setPadding(left, 0, right, 0);
                return insets;
            }
        });
        setContentView(root);
        applySystemBarAppearance();
    }

    private void setHeight(View v, int h) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp.height != h) { lp.height = h; v.setLayoutParams(lp); }
    }

    // ------------------------------------------------------------------ WebView
    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(BuildConfig.ENABLE_JAVASCRIPT);
        s.setDomStorageEnabled(BuildConfig.ENABLE_DOM_STORAGE);
        s.setDatabaseEnabled(BuildConfig.ENABLE_DOM_STORAGE);
        s.setSupportZoom(BuildConfig.ENABLE_ZOOM);
        s.setBuiltInZoomControls(BuildConfig.ENABLE_ZOOM);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(!BuildConfig.AUTOPLAY_MEDIA);
        s.setTextZoom(BuildConfig.TEXT_ZOOM);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setGeolocationEnabled(BuildConfig.PERM_GEOLOCATION);
        s.setCacheMode(BuildConfig.CACHE_MODE);
        s.setAllowContentAccess(true);
        s.setAllowFileAccess(BuildConfig.IS_LOCAL_SOURCE);
        s.setMixedContentMode(BuildConfig.ALLOW_MIXED_CONTENT
                ? WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                : WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        String ua = BuildConfig.CUSTOM_USER_AGENT;
        if (ua != null && ua.trim().length() > 0) {
            s.setUserAgentString(ua.trim());
        } else if (BuildConfig.DESKTOP_MODE) {
            s.setUserAgentString(DESKTOP_UA + " WebifyApp/" + BuildConfig.VERSION_NAME);
        } else {
            s.setUserAgentString(s.getUserAgentString() + " WebifyApp/" + BuildConfig.VERSION_NAME);
        }
        if (Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(BuildConfig.SAFE_BROWSING);
        applyForceDark(s);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(webView, BuildConfig.THIRD_PARTY_COOKIES);

        if (BuildConfig.CLEAR_CACHE_ON_START) {
            webView.clearCache(true);
            webView.clearHistory();
        }

        webView.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        webView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        webView.setVerticalScrollBarEnabled(!BuildConfig.HIDE_SCROLLBARS);
        webView.setHorizontalScrollBarEnabled(!BuildConfig.HIDE_SCROLLBARS);
        if (BuildConfig.DISABLE_TEXT_SELECTION) {
            webView.setLongClickable(false);
            webView.setHapticFeedbackEnabled(false);
            webView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override public boolean onLongClick(View v) {
                    // Tetap izinkan seleksi di kolom input (hit test EDIT_TEXT)
                    WebView.HitTestResult r = webView.getHitTestResult();
                    return r == null || r.getType() != WebView.HitTestResult.EDIT_TEXT_TYPE;
                }
            });
        }
        webView.setBackgroundColor(Color.WHITE);
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webViewClient = new WebifyWebViewClient(this);
        chromeClient = new WebifyWebChromeClient(this);
        webView.setWebViewClient(webViewClient);
        webView.setWebChromeClient(chromeClient);
        webView.setDownloadListener(new WebifyDownloadListener(this));
        if (BuildConfig.ENABLE_JAVASCRIPT) {
            webView.addJavascriptInterface(new WebifyJsBridge(this), "WebifyBridge");
        }

        if (BuildConfig.ENABLE_SWIPE_NAVIGATION) {
            final GestureDetector detector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                    if (e1 == null || e2 == null) return false;
                    float dx = e2.getX() - e1.getX();
                    float dy = e2.getY() - e1.getY();
                    if (Math.abs(dx) > dp(120) && Math.abs(dx) > Math.abs(dy) * 2 && Math.abs(vx) > 800) {
                        if (dx > 0 && webView.canGoBack()) { webView.goBack(); return true; }
                        if (dx < 0 && webView.canGoForward()) { webView.goForward(); return true; }
                    }
                    return false;
                }
            });
            webView.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View v, MotionEvent event) {
                    detector.onTouchEvent(event);
                    return false;
                }
            });
        }
    }

    private void loadInitial(Intent intent) {
        String deepLink = extractDeepLink(intent);
        String url = deepLink != null ? deepLink : BuildConfig.TARGET_URL;
        if (BuildConfig.OFFLINE_MODE == BuildConfig.OFFLINE_CACHE_FIRST && !isOnline()) {
            webView.getSettings().setCacheMode(WebSettings.LOAD_CACHE_ELSE_NETWORK);
        }
        if (!BuildConfig.IS_LOCAL_SOURCE && !isOnline() && BuildConfig.OFFLINE_MODE != BuildConfig.OFFLINE_CACHE_FIRST) {
            showOffline(url);
            return;
        }
        webView.loadUrl(url);
    }

    private String extractDeepLink(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return null;
        Uri data = intent.getData();
        if (data == null) return null;
        String scheme = data.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
        return webViewClient != null && webViewClient.isInternalHost(data.getHost()) ? data.toString() : null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        String link = extractDeepLink(intent);
        if (link != null) webView.loadUrl(link);
    }

    // ------------------------------------------------------------------ Splash
    private void setupSplash() {
        if (!BuildConfig.ENABLE_SPLASH) { splashDismissed = true; return; }
        splashView = new WebifySplashView(this, BuildConfig.SPLASH_BG_COLOR, BuildConfig.SPLASH_SHOW_LOADER, BuildConfig.PRIMARY_COLOR);
        ((FrameLayout) getWindow().getDecorView().findViewById(android.R.id.content))
                .addView(splashView, new FrameLayout.LayoutParams(-1, -1));
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                minSplashElapsed = true;
                if (firstPageLoaded || showingOffline) dismissSplash();
            }
        }, Math.max(300, BuildConfig.SPLASH_DURATION_MS));
        // pengaman: splash tidak boleh menahan lebih dari 8 detik
        handler.postDelayed(new Runnable() {
            @Override public void run() { dismissSplash(); }
        }, Math.max(8000, BuildConfig.SPLASH_DURATION_MS + 3000));
    }

    private void dismissSplash() {
        if (splashDismissed || splashView == null) return;
        splashDismissed = true;
        splashView.animate().alpha(0f).setDuration(350).withEndAction(new Runnable() {
            @Override public void run() {
                ViewGroup parent = (ViewGroup) splashView.getParent();
                if (parent != null) parent.removeView(splashView);
                splashView = null;
            }
        }).start();
    }

    private void dismissSplashNow() {
        minSplashElapsed = true;
        firstPageLoaded = true;
        dismissSplash();
    }

    // ------------------------------------------------------------------ Callbacks dari client
    void onPageStarted(String url) {
        if (BuildConfig.SHOW_TOP_PROGRESS) {
            topProgress.setVisibility(View.VISIBLE);
            topProgress.setProgress(5);
        }
    }

    void onPageFinished(String url) {
        refreshLayout.setRefreshing(false);
        topProgress.setVisibility(View.GONE);
        if (!showingOffline) firstPageLoaded = true;
        if (minSplashElapsed) dismissSplash();
    }

    void onProgress(int progress) {
        if (!BuildConfig.SHOW_TOP_PROGRESS) return;
        topProgress.setProgress(progress);
        topProgress.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
    }

    void showOffline(String failedUrl) {
        if (failedUrl != null && !failedUrl.startsWith("data:") && !failedUrl.startsWith("about:")) lastFailedUrl = failedUrl;
        showingOffline = true;
        refreshLayout.setRefreshing(false);
        topProgress.setVisibility(View.GONE);
        String html;
        try {
            html = new String(Base64.decode(BuildConfig.OFFLINE_HTML_BASE64, Base64.DEFAULT), "UTF-8");
        } catch (Exception e) {
            html = "<html><body style='font-family:sans-serif;text-align:center;padding-top:40vh'>"
                    + "<h3>Tidak ada koneksi</h3><a href='webify://retry'>Coba Lagi</a></body></html>";
        }
        webView.loadDataWithBaseURL("file:///android_asset/", html, "text/html", "UTF-8", null);
        if (minSplashElapsed) dismissSplash();
    }

    void retry() {
        showingOffline = false;
        String target = lastFailedUrl != null ? lastFailedUrl : BuildConfig.TARGET_URL;
        webView.getSettings().setCacheMode(isOnline() ? BuildConfig.CACHE_MODE : WebSettings.LOAD_CACHE_ELSE_NETWORK);
        webView.loadUrl(target);
    }

    void reload() {
        if (showingOffline) { retry(); return; }
        webView.reload();
    }

    boolean isOnline() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            if (Build.VERSION.SDK_INT >= 23) {
                android.net.Network n = cm.getActiveNetwork();
                if (n == null) return false;
                android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(n);
                return caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        } catch (Exception e) {
            return true;
        }
    }

    WebifyWebViewClient getWebifyClient() { return webViewClient; }

    // ------------------------------------------------------------------ Lifecycle & system
    /** Mode gelap paksa: API 33+ algorithmic darkening, API 29–32 setForceDark. */
    @SuppressWarnings("deprecation")
    private void applyForceDark(WebSettings s) {
        if (!BuildConfig.FORCE_DARK) return;
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                s.setAlgorithmicDarkeningAllowed(true);
            } else if (Build.VERSION.SDK_INT >= 29) {
                s.setForceDark(WebSettings.FORCE_DARK_ON);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Force dark tidak didukung", t);
        }
    }

    private long lastBackPress = 0L;

    @Override
    public void onBackPressed() {
        if (chromeClient != null && chromeClient.isCustomViewShowing()) {
            chromeClient.onHideCustomView();
            return;
        }
        if (showingOffline && webView.canGoBack()) {
            showingOffline = false;
        }
        if (webView.canGoBack()) {
            webView.goBack();
        } else if (BuildConfig.CONFIRM_EXIT && System.currentTimeMillis() - lastBackPress > 2000) {
            lastBackPress = System.currentTimeMillis();
            android.widget.Toast.makeText(this, "Tekan kembali sekali lagi untuk keluar", android.widget.Toast.LENGTH_SHORT).show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE_CHOOSER && chromeClient != null) {
            chromeClient.onFileChooserResult(resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (chromeClient != null) chromeClient.onPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (!showingOffline) webView.saveState(outState);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && BuildConfig.FULLSCREEN) applySystemBarAppearance();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        webView.resumeTimers();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            ((ViewGroup) webView.getParent()).removeView(webView);
            webView.destroy();
        } catch (Exception e) {
            Log.w(TAG, "destroy webview", e);
        }
        super.onDestroy();
    }

    // ------------------------------------------------------------------ Util
    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    static boolean isLightColor(int c) {
        double lum = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0;
        return lum > 0.6;
    }

    boolean hasPermission(String perm) {
        return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    static final String DESKTOP_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";
    static final String PERM_CAMERA = Manifest.permission.CAMERA;
    static final String PERM_AUDIO = Manifest.permission.RECORD_AUDIO;
    static final String PERM_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;
    static final String PERM_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
}
