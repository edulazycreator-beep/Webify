package {{PACKAGE}};

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.webkit.WebView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.messaging.FirebaseMessaging;

/** Helper Firebase Cloud Messaging: token disimpan & diteruskan ke website via window.onFcmTokenReceived(token). */
final class WebifyFcm {

    private static final String TAG = "WebifyFcm";
    private static final String PREFS = "webify_fcm";
    private static final String KEY_TOKEN = "token";

    private WebifyFcm() {}

    static void init(final MainActivity a) {
        WebifyNotificationReceiver.ensureChannel(a);
        ensurePushChannel(a);
        a.withNotificationPermission(new Runnable() {
            @Override public void run() { /* izin notifikasi diberikan */ }
        });
        try {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
                @Override public void onComplete(Task<String> task) {
                    if (task.isSuccessful() && task.getResult() != null) saveToken(a, task.getResult());
                    else Log.w(TAG, "Gagal mengambil token FCM", task.getException());
                }
            });
        } catch (Throwable t) {
            Log.e(TAG, "Firebase belum terinisialisasi (cek google-services.json)", t);
        }
    }

    static void ensurePushChannel(Context ctx) {
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel("webify_push") == null) {
                nm.createNotificationChannel(new android.app.NotificationChannel(
                        "webify_push", "Push Notification", android.app.NotificationManager.IMPORTANCE_HIGH));
            }
        }
    }

    static void saveToken(Context ctx, String token) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_TOKEN, token).apply();
    }

    static String savedToken(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TOKEN, null);
    }

    /** Dipanggil setiap halaman selesai dimuat: panggil window.onFcmTokenReceived(token) bila tersedia. */
    static void deliverToken(MainActivity a, WebView view) {
        String token = savedToken(a);
        if (token == null) return;
        view.evaluateJavascript("(function(){try{window.WebifyFcmToken=" + MainActivity.jsonString(token)
                + ";if(typeof window.onFcmTokenReceived==='function')window.onFcmTokenReceived(window.WebifyFcmToken);}catch(e){}})();", null);
    }

    /** WebifyBridge.getFcmToken(callback). */
    static void requestToken(final MainActivity a, final String callback) {
        String cached = savedToken(a);
        if (cached != null) {
            a.deliverJs(callback, MainActivity.jsonString(cached));
            return;
        }
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(new OnCompleteListener<String>() {
            @Override public void onComplete(Task<String> task) {
                if (task.isSuccessful() && task.getResult() != null) {
                    saveToken(a, task.getResult());
                    a.deliverJs(callback, MainActivity.jsonString(task.getResult()));
                } else {
                    a.deliverJs(callback, "null");
                }
            }
        });
    }
}
