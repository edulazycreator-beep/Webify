package {{PACKAGE}};

import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;

/**
 * Jembatan JavaScript ↔ native: tersedia di website sebagai window.WebifyBridge.
 *
 * Contoh: WebifyBridge.showToast('Halo'); WebifyBridge.vibrate(80);
 *         WebifyBridge.shareText('Cek ini!'); WebifyBridge.getAppVersion(); WebifyBridge.closeApp();
 */
public class WebifyJsBridge {

    private static final String TAG = "WebifyBridge";
    private final MainActivity activity;

    public WebifyJsBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void showToast(final String message) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                Toast.makeText(activity, message == null ? "" : message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @JavascriptInterface
    public void vibrate(long durationMs) {
        long ms = Math.max(1, Math.min(durationMs, 5000));
        try {
            Vibrator v;
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) activity.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                v = vm != null ? vm.getDefaultVibrator() : null;
            } else {
                v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (v == null || !v.hasVibrator()) return;
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(ms);
            }
        } catch (Exception e) {
            Log.w(TAG, "vibrate gagal", e);
        }
    }

    @JavascriptInterface
    public void vibrate() {
        vibrate(60);
    }

    @JavascriptInterface
    public void shareText(final String text) {
        shareText(text, null);
    }

    @JavascriptInterface
    public void shareText(final String text, final String title) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, text == null ? "" : text);
                if (title != null) send.putExtra(Intent.EXTRA_SUBJECT, title);
                try {
                    activity.startActivity(Intent.createChooser(send, title != null ? title : "Bagikan"));
                } catch (Exception e) {
                    Toast.makeText(activity, "Tidak ada aplikasi untuk berbagi", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @JavascriptInterface
    public String getAppVersion() {
        return BuildConfig.VERSION_NAME;
    }

    @JavascriptInterface
    public int getAppVersionCode() {
        return BuildConfig.VERSION_CODE;
    }

    @JavascriptInterface
    public String getPackageName() {
        return BuildConfig.APPLICATION_ID;
    }

    @JavascriptInterface
    public boolean isNativeApp() {
        return true;
    }

    @JavascriptInterface
    public String getDeviceInfo() {
        return "{\"platform\":\"android\",\"sdk\":" + Build.VERSION.SDK_INT
                + ",\"release\":\"" + esc(Build.VERSION.RELEASE) + "\",\"model\":\"" + esc(Build.MODEL)
                + "\",\"manufacturer\":\"" + esc(Build.MANUFACTURER) + "\",\"appVersion\":\"" + esc(BuildConfig.VERSION_NAME) + "\"}";
    }

    @JavascriptInterface
    public void copyToClipboard(final String text) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) {
                    cm.setPrimaryClip(ClipData.newPlainText("text", text == null ? "" : text));
                    if (Build.VERSION.SDK_INT < 33) Toast.makeText(activity, "Disalin", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @JavascriptInterface
    public void openExternal(final String url) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                activity.getWebifyClient().openExternal(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            }
        });
    }

    @JavascriptInterface
    public void retry() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { activity.retry(); }
        });
    }

    @JavascriptInterface
    public void closeApp() {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { activity.finishAndRemoveTask(); }
        });
    }

    /** Dipakai helper unduhan blob: — menyimpan base64 ke folder Downloads. */
    @JavascriptInterface
    public void saveBase64File(final String base64, final String fileName, final String mime) {
        activity.runOnUiThread(new Runnable() {
            @Override public void run() { saveBase64(activity, base64, fileName, mime); }
        });
    }

    // ------------------------------------------------------------------ Helpers statis
    static void saveBase64(MainActivity activity, String base64, String fileName, String mime) {
        try {
            saveBytes(activity, Base64.decode(base64, Base64.DEFAULT), fileName, mime);
        } catch (Exception e) {
            Toast.makeText(activity, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    static void saveBytes(MainActivity activity, byte[] data, String fileName, String mime) {
        String name = (fileName == null || fileName.length() == 0) ? "download_" + System.currentTimeMillis() : fileName;
        String type = (mime == null || mime.length() == 0) ? "application/octet-stream" : mime;
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentResolver cr = activity.getContentResolver();
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                cv.put(MediaStore.MediaColumns.MIME_TYPE, type);
                cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new IllegalStateException("MediaStore menolak penyimpanan");
                OutputStream os = cr.openOutputStream(uri);
                if (os == null) throw new IllegalStateException("Tidak dapat menulis file");
                try { os.write(data); } finally { os.close(); }
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists()) dir.mkdirs();
                File out = new File(dir, name);
                FileOutputStream fos = new FileOutputStream(out);
                try { fos.write(data); } finally { fos.close(); }
                DownloadManager dm = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
                if (dm != null) {
                    dm.addCompletedDownload(name, name, true, type, out.getAbsolutePath(), data.length, true);
                }
            }
            Toast.makeText(activity, "Tersimpan di Downloads: " + name, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "saveBytes", e);
            Toast.makeText(activity, "Gagal menyimpan: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
