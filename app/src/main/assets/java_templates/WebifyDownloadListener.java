package {{PACKAGE}};

import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.widget.Toast;

/**
 * Meneruskan unduhan WebView ke DownloadManager sistem (dengan notifikasi progres otomatis).
 * Mendukung http(s), data: dan blob: (via bridge JS).
 */
public class WebifyDownloadListener implements DownloadListener {

    private static final String TAG = "WebifyDownload";
    private final MainActivity activity;

    public WebifyDownloadListener(MainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
        try {
            if (!BuildConfig.ENABLE_DOWNLOAD_MANAGER) {
                activity.getWebifyClient().openExternal(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return;
            }
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimeType);

            if (url.startsWith("blob:")) {
                String js = "window.__webifyBlobDownload && window.__webifyBlobDownload('" + url.replace("'", "\\'") + "','"
                        + fileName.replace("'", "\\'") + "');";
                activity.webView.evaluateJavascript(js, null);
                return;
            }
            if (url.startsWith("data:")) {
                int comma = url.indexOf(',');
                String meta = url.substring(5, Math.max(5, comma));
                String mime = meta.contains(";") ? meta.substring(0, meta.indexOf(';')) : meta;
                if (meta.contains("base64")) {
                    WebifyJsBridge.saveBase64(activity, url.substring(comma + 1), fileName, mime.length() > 0 ? mime : mimeType);
                } else {
                    String text = Uri.decode(url.substring(comma + 1));
                    WebifyJsBridge.saveBytes(activity, text.getBytes("UTF-8"), fileName, mime.length() > 0 ? mime : "text/plain");
                }
                return;
            }
            if (Build.VERSION.SDK_INT <= 28
                    && activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, MainActivity.REQ_PERMISSIONS_STORAGE);
                Toast.makeText(activity, "Izinkan akses penyimpanan lalu ketuk unduh lagi", Toast.LENGTH_LONG).show();
                return;
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            if (mimeType != null && mimeType.length() > 0) request.setMimeType(mimeType);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) request.addRequestHeader("Cookie", cookies);
            request.addRequestHeader("User-Agent", userAgent);
            request.setTitle(fileName);
            request.setDescription("Mengunduh via " + activity.getString(R.string.app_name));
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) throw new IllegalStateException("DownloadManager tidak tersedia");
            dm.enqueue(request);
            Toast.makeText(activity, "Mengunduh " + fileName + "…", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "Unduhan gagal", e);
            Toast.makeText(activity, "Unduhan gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
