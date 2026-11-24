package {{PACKAGE}};

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * Menerima pesan FCM. Payload "notification" ditampilkan otomatis oleh FCM saat aplikasi di background;
 * di foreground (atau payload data-only) Webify menampilkan notifikasi sendiri.
 */
public class WebifyFirebaseMessagingService extends FirebaseMessagingService {

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        Log.i("WebifyFcm", "Token FCM baru");
        WebifyFcm.saveToken(this, token);
    }

    @Override
    public void onMessageReceived(RemoteMessage message) {
        super.onMessageReceived(message);
        WebifyFcm.ensurePushChannel(this);
        String title = null;
        String body = null;
        if (message.getNotification() != null) {
            title = message.getNotification().getTitle();
            body = message.getNotification().getBody();
        }
        Map<String, String> data = message.getData();
        if (data != null) {
            if (title == null) title = data.get("title");
            if (body == null) body = data.get("body");
        }
        if (title == null && body == null) return;
        int id = (int) (System.currentTimeMillis() & 0x7FFFFFF);
        WebifyNotificationReceiver.show(this, id, title == null ? BuildConfig.APP_NAME : title, body == null ? "" : body);
    }
}
