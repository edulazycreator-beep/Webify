package {{PACKAGE}};

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Message;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Menangani upload file (galeri/kamera), progres, geolokasi, WebRTC (kamera/mikrofon),
 * video fullscreen, dan jendela baru (target=_blank).
 */
public class WebifyWebChromeClient extends WebChromeClient {

    private static final String TAG = "WebifyChrome";
    private final MainActivity activity;

    // file chooser
    private ValueCallback<Uri[]> filePathCallback;
    private FileChooserParams pendingChooserParams;
    private File cameraFile;
    private Uri cameraUri;

    // geolocation
    private String pendingGeoOrigin;
    private GeolocationPermissions.Callback pendingGeoCallback;

    // WebRTC
    private PermissionRequest pendingWebRequest;

    // fullscreen video
    private View customView;
    private CustomViewCallback customViewCallback;
    private int originalOrientation;

    public WebifyWebChromeClient(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onProgressChanged(WebView view, int newProgress) {
        activity.onProgress(newProgress);
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage m) {
        Log.d("WebifyConsole", m.message() + " (" + m.sourceId() + ":" + m.lineNumber() + ")");
        return true;
    }

    // ------------------------------------------------------------------ File upload
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
        if (!BuildConfig.ENABLE_FILE_UPLOAD) {
            callback.onReceiveValue(null);
            return true;
        }
        if (filePathCallback != null) filePathCallback.onReceiveValue(null);
        filePathCallback = callback;
        pendingChooserParams = params;

        if (BuildConfig.PERM_CAMERA && acceptsImagesOrVideo(params) && !activity.hasPermission(MainActivity.PERM_CAMERA)) {
            activity.requestPermissions(new String[]{MainActivity.PERM_CAMERA}, MainActivity.REQ_PERMISSIONS_FILE);
            return true;
        }
        launchChooser();
        return true;
    }

    private boolean acceptsImagesOrVideo(FileChooserParams params) {
        String[] types = params.getAcceptTypes();
        if (types == null || types.length == 0) return true;
        for (String t : types) {
            if (t == null || t.length() == 0 || t.startsWith("image") || t.startsWith("video") || t.equals("*/*")) return true;
        }
        return false;
    }

    private void launchChooser() {
        FileChooserParams params = pendingChooserParams;
        Intent content;
        try {
            content = params.createIntent();
        } catch (Exception e) {
            content = new Intent(Intent.ACTION_GET_CONTENT);
            content.setType("*/*");
        }
        content.addCategory(Intent.CATEGORY_OPENABLE);
        if (params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE) {
            content.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        }

        List<Intent> extras = new ArrayList<Intent>();
        cameraFile = null;
        cameraUri = null;
        if (BuildConfig.PERM_CAMERA && acceptsImagesOrVideo(params) && activity.hasPermission(MainActivity.PERM_CAMERA)) {
            try {
                File dir = new File(activity.getCacheDir(), "webify_capture");
                if (!dir.exists()) dir.mkdirs();
                cameraFile = new File(dir, "IMG_" + System.currentTimeMillis() + ".jpg");
                cameraUri = WebifyFileProvider.getUriForFile(activity, cameraFile);
                Intent cam = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                cam.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                cam.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                cam.setClipData(ClipData.newRawUri("output", cameraUri));
                if (cam.resolveActivity(activity.getPackageManager()) != null || Build.VERSION.SDK_INT >= 30) {
                    extras.add(cam);
                }
            } catch (Exception e) {
                Log.w(TAG, "kamera tidak tersedia", e);
            }
        }

        Intent chooser = Intent.createChooser(content, params.getTitle() != null ? params.getTitle() : "Pilih file");
        if (!extras.isEmpty()) chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, extras.toArray(new Intent[0]));
        try {
            activity.startActivityForResult(chooser, MainActivity.REQ_FILE_CHOOSER);
        } catch (Exception e) {
            Toast.makeText(activity, "Tidak ada aplikasi pemilih file", Toast.LENGTH_SHORT).show();
            filePathCallback.onReceiveValue(null);
            filePathCallback = null;
        }
    }

    void onFileChooserResult(int resultCode, Intent data) {
        if (filePathCallback == null) return;
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data != null && data.getClipData() != null && data.getClipData().getItemCount() > 0
                    && !(cameraUri != null && cameraUri.equals(data.getClipData().getItemAt(0).getUri()))) {
                ClipData clip = data.getClipData();
                results = new Uri[clip.getItemCount()];
                for (int i = 0; i < clip.getItemCount(); i++) results[i] = clip.getItemAt(i).getUri();
            } else if (data != null && data.getData() != null) {
                results = new Uri[]{data.getData()};
            } else if (cameraFile != null && cameraFile.exists() && cameraFile.length() > 0) {
                results = new Uri[]{cameraUri};
            }
        }
        filePathCallback.onReceiveValue(results);
        filePathCallback = null;
        pendingChooserParams = null;
    }

    // ------------------------------------------------------------------ Geolocation
    @Override
    public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
        if (!BuildConfig.PERM_GEOLOCATION) {
            callback.invoke(origin, false, false);
            return;
        }
        if (activity.hasPermission(MainActivity.PERM_FINE_LOCATION) || activity.hasPermission(MainActivity.PERM_COARSE_LOCATION)) {
            callback.invoke(origin, true, false);
            return;
        }
        pendingGeoOrigin = origin;
        pendingGeoCallback = callback;
        activity.requestPermissions(new String[]{MainActivity.PERM_FINE_LOCATION, MainActivity.PERM_COARSE_LOCATION},
                MainActivity.REQ_PERMISSIONS_GEO);
    }

    // ------------------------------------------------------------------ WebRTC
    @Override
    public void onPermissionRequest(final PermissionRequest request) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { handleWebPermission(request); }
        });
    }

    private void handleWebPermission(PermissionRequest request) {
        List<String> needed = new ArrayList<String>();
        List<String> allowedRes = new ArrayList<String>();
        for (String r : request.getResources()) {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && BuildConfig.PERM_CAMERA) {
                allowedRes.add(r);
                if (!activity.hasPermission(MainActivity.PERM_CAMERA)) needed.add(MainActivity.PERM_CAMERA);
            } else if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && BuildConfig.PERM_MICROPHONE) {
                allowedRes.add(r);
                if (!activity.hasPermission(MainActivity.PERM_AUDIO)) needed.add(MainActivity.PERM_AUDIO);
            } else if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) {
                allowedRes.add(r);
            }
        }
        if (allowedRes.isEmpty()) {
            request.deny();
            return;
        }
        if (needed.isEmpty()) {
            request.grant(allowedRes.toArray(new String[0]));
            return;
        }
        if (pendingWebRequest != null) pendingWebRequest.deny();
        pendingWebRequest = request;
        activity.requestPermissions(needed.toArray(new String[0]), MainActivity.REQ_PERMISSIONS_WEB);
    }

    @Override
    public void onPermissionRequestCanceled(PermissionRequest request) {
        if (pendingWebRequest == request) pendingWebRequest = null;
    }

    void onPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == MainActivity.REQ_PERMISSIONS_FILE) {
            if (filePathCallback != null && pendingChooserParams != null) launchChooser();
        } else if (requestCode == MainActivity.REQ_PERMISSIONS_GEO) {
            boolean granted = false;
            for (int g : grantResults) if (g == PackageManager.PERMISSION_GRANTED) granted = true;
            if (pendingGeoCallback != null) pendingGeoCallback.invoke(pendingGeoOrigin, granted, false);
            pendingGeoCallback = null;
            pendingGeoOrigin = null;
        } else if (requestCode == MainActivity.REQ_PERMISSIONS_WEB && pendingWebRequest != null) {
            List<String> grant = new ArrayList<String>();
            for (String r : pendingWebRequest.getResources()) {
                if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r) && BuildConfig.PERM_CAMERA && activity.hasPermission(MainActivity.PERM_CAMERA)) grant.add(r);
                if (PermissionRequest.RESOURCE_AUDIO_CAPTURE.equals(r) && BuildConfig.PERM_MICROPHONE && activity.hasPermission(MainActivity.PERM_AUDIO)) grant.add(r);
                if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) grant.add(r);
            }
            if (grant.isEmpty()) pendingWebRequest.deny();
            else pendingWebRequest.grant(grant.toArray(new String[0]));
            pendingWebRequest = null;
        }
    }

    // ------------------------------------------------------------------ Fullscreen video
    @Override
    public void onShowCustomView(View view, CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        customView = view;
        customViewCallback = callback;
        originalOrientation = activity.getRequestedOrientation();
        FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
        view.setBackgroundColor(Color.BLACK);
        decor.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setImmersive(true);
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
    }

    @Override
    public void onHideCustomView() {
        if (customView == null) return;
        FrameLayout decor = (FrameLayout) activity.getWindow().getDecorView();
        decor.removeView(customView);
        customView = null;
        setImmersive(BuildConfig.FULLSCREEN);
        activity.setRequestedOrientation(originalOrientation);
        if (customViewCallback != null) customViewCallback.onCustomViewHidden();
        customViewCallback = null;
    }

    boolean isCustomViewShowing() {
        return customView != null;
    }

    private void setImmersive(boolean on) {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = activity.getWindow().getInsetsController();
            if (c == null) return;
            if (on) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsets.Type.systemBars());
            }
        } else {
            View d = activity.getWindow().getDecorView();
            int base = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
            if (!BuildConfig.STATUS_BAR_LIGHT_ICONS) base |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            d.setSystemUiVisibility(on
                    ? base | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    : base);
        }
    }

    // ------------------------------------------------------------------ Jendela baru (target=_blank / window.open)
    @Override
    public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
        final WebView temp = new WebView(activity);
        temp.setWebViewClient(new WebViewClient() {
            private boolean handled = false;

            private boolean handle(String url) {
                if (handled || url == null) return true;
                handled = true;
                if (!activity.getWebifyClient().route(activity.webView, url, true)) {
                    activity.webView.loadUrl(url);
                }
                temp.post(new Runnable() { @Override public void run() { temp.destroy(); } });
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest request) {
                return handle(request.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return handle(url);
            }

            @Override
            public void onPageStarted(WebView v, String url, android.graphics.Bitmap favicon) {
                if (url != null && !url.equals("about:blank")) {
                    v.stopLoading();
                    handle(url);
                }
            }
        });
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(temp);
        resultMsg.sendToTarget();
        return true;
    }
}
