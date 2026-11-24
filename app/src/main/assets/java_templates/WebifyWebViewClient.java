package {{PACKAGE}};

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.util.Base64;
import android.util.Log;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.net.URISyntaxException;

/**
 * Mengatur routing URL (internal vs eksternal), skema intent native, injeksi CSS/JS,
 * dan fallback halaman offline.
 */
public class WebifyWebViewClient extends WebViewClient {

    private static final String TAG = "WebifyClient";
    private final MainActivity activity;
    private final String[] internalHosts;

    public WebifyWebViewClient(MainActivity activity) {
        this.activity = activity;
        String main = null;
        try { main = Uri.parse(BuildConfig.TARGET_URL).getHost(); } catch (Exception ignored) { }
        String[] extra = BuildConfig.ALLOWED_DOMAINS;
        int n = extra.length + (main != null ? 1 : 0);
        internalHosts = new String[n];
        int i = 0;
        if (main != null) internalHosts[i++] = normalize(main);
        for (String d : extra) internalHosts[i++] = normalize(d);
    }

    private static String normalize(String host) {
        String h = host.trim().toLowerCase();
        if (h.startsWith("*.")) h = h.substring(2);
        if (h.startsWith("www.")) h = h.substring(4);
        return h;
    }

    public boolean isInternalHost(String host) {
        if (host == null) return false;
        String h = normalize(host);
        for (String allowed : internalHosts) {
            if (allowed.length() == 0) continue;
            if (h.equals(allowed) || h.endsWith("." + allowed)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ Routing
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
        return route(view, request.getUrl().toString(), request.isForMainFrame());
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return route(view, url, true);
    }

    boolean route(WebView view, String url, boolean mainFrame) {
        if (url == null) return false;
        if (url.startsWith("webify://retry")) {
            activity.retry();
            return true;
        }
        Uri uri = Uri.parse(url);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();

        if (scheme.equals("http") || scheme.equals("https")) {
            if (isInternalHost(uri.getHost()) || !mainFrame) return false;
            if (!BuildConfig.ENABLE_EXTERNAL_INTENTS) return false;
            openExternal(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        }
        if (scheme.equals("file") || scheme.equals("about") || scheme.equals("data") || scheme.equals("blob") || scheme.equals("javascript")) {
            return false;
        }
        if (!BuildConfig.ENABLE_EXTERNAL_INTENTS) {
            Toast.makeText(activity, "Tautan tidak didukung: " + scheme, Toast.LENGTH_SHORT).show();
            return true;
        }
        if (scheme.equals("intent")) {
            return handleIntentScheme(view, url);
        }
        Intent intent;
        if (scheme.equals("mailto")) {
            intent = new Intent(Intent.ACTION_SENDTO, uri);
        } else if (scheme.equals("tel")) {
            intent = new Intent(Intent.ACTION_DIAL, uri);
        } else {
            // whatsapp:, tg:, sms:, geo:, market:, dan skema aplikasi lain
            intent = new Intent(Intent.ACTION_VIEW, uri);
        }
        if (!openExternal(intent)) {
            if (scheme.equals("whatsapp")) {
                openExternal(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")));
            } else if (scheme.equals("tg")) {
                openExternal(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=org.telegram.messenger")));
            } else {
                Toast.makeText(activity, "Tidak ada aplikasi untuk membuka tautan ini", Toast.LENGTH_SHORT).show();
            }
        }
        return true;
    }

    private boolean handleIntentScheme(WebView view, String url) {
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            intent.setComponent(null);
            intent.setSelector(null);
            try {
                activity.startActivity(intent);
                return true;
            } catch (ActivityNotFoundException e) {
                String fallback = intent.getStringExtra("browser_fallback_url");
                if (fallback != null && fallback.length() > 0) {
                    Uri f = Uri.parse(fallback);
                    if (isInternalHost(f.getHost())) view.loadUrl(fallback);
                    else openExternal(new Intent(Intent.ACTION_VIEW, f));
                    return true;
                }
                String pkg = intent.getPackage();
                if (pkg != null) {
                    openExternal(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
                    return true;
                }
            }
        } catch (URISyntaxException e) {
            Log.w(TAG, "intent: tidak valid " + url, e);
        }
        Toast.makeText(activity, "Aplikasi tujuan tidak terpasang", Toast.LENGTH_SHORT).show();
        return true;
    }

    boolean openExternal(Intent intent) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            return false;
        } catch (SecurityException e) {
            Log.w(TAG, "openExternal ditolak", e);
            return false;
        }
    }

    // ------------------------------------------------------------------ Lifecycle halaman
    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        activity.onPageStarted(url);
        if (isInjectable(url)) {
            injectCss(view);
            injectJs(view, BuildConfig.CUSTOM_JS_ON_PAGE_START);
        }
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        if (isInjectable(url)) {
            injectCss(view);
            injectJs(view, BuildConfig.CUSTOM_JS_ON_PAGE_FINISHED);
            injectBridgeHelpers(view);
        }
        activity.onPageFinished(url);
    }

    private boolean isInjectable(String url) {
        return BuildConfig.ENABLE_JAVASCRIPT && url != null && !url.startsWith("data:") && !activity.showingOffline;
    }

    private void injectCss(WebView view) {
        String css = BuildConfig.CUSTOM_CSS;
        if (css == null || css.trim().length() == 0) return;
        String b64 = b64(css);
        String js = "(function(){try{var id='webify-custom-css';var s=document.getElementById(id);"
                + "if(!s){s=document.createElement('style');s.id=id;(document.head||document.documentElement).appendChild(s);}"
                + "s.textContent=decodeURIComponent(escape(atob('" + b64 + "')));}catch(e){console.error('Webify CSS',e);}})();";
        view.evaluateJavascript(js, null);
    }

    private void injectJs(WebView view, String code) {
        if (code == null || code.trim().length() == 0) return;
        String js = "(function(){try{var c=decodeURIComponent(escape(atob('" + b64(code) + "')));(new Function(c))();}"
                + "catch(e){console.error('Webify JS',e);}})();";
        view.evaluateJavascript(js, null);
    }

    /** Helper JS: unduhan blob: diteruskan ke bridge native. */
    private void injectBridgeHelpers(WebView view) {
        String js = "(function(){if(window.__webifyHelpers)return;window.__webifyHelpers=true;"
                + "window.Webify={isApp:true,version:'" + BuildConfig.VERSION_NAME + "'};"
                + "window.__webifyBlobDownload=function(u,n){fetch(u).then(function(r){return r.blob();}).then(function(b){"
                + "var fr=new FileReader();fr.onloadend=function(){var d=fr.result;var i=d.indexOf(',');"
                + "WebifyBridge.saveBase64File(d.substring(i+1),n||'download',b.type||'application/octet-stream');};fr.readAsDataURL(b);"
                + "}).catch(function(e){WebifyBridge.showToast('Gagal mengunduh: '+e);});};})();";
        view.evaluateJavascript(js, null);
    }

    static String b64(String s) {
        try {
            return Base64.encodeToString(s.getBytes("UTF-8"), Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ Error
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        super.onReceivedError(view, request, error);
        if (request.isForMainFrame()) {
            Log.w(TAG, "Main frame error " + error.getErrorCode() + ": " + error.getDescription());
            activity.showOffline(request.getUrl().toString());
        }
    }

    @Override
    public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
        Log.e(TAG, "Renderer WebView berhenti (crash=" + (Build.VERSION.SDK_INT >= 26 && detail.didCrash()) + ")");
        activity.recreate();
        return true;
    }
}
