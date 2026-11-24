package {{PACKAGE}};

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** Menampilkan notifikasi lokal yang dijadwalkan lewat WebifyBridge.scheduleNotification(). */
public class WebifyNotificationReceiver extends BroadcastReceiver {

    static final String CHANNEL_ID = "webify_local";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_BODY = "body";
    static final String EXTRA_ID = "id";

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra(EXTRA_TITLE);
        String body = intent.getStringExtra(EXTRA_BODY);
        int id = intent.getIntExtra(EXTRA_ID, (int) (System.currentTimeMillis() & 0x7FFFFFF));
        show(context, id, title == null ? BuildConfig.APP_NAME : title, body == null ? "" : body);
    }

    static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Pengingat", NotificationManager.IMPORTANCE_HIGH);
                ch.setDescription("Notifikasi dari " + BuildConfig.APP_NAME);
                nm.createNotificationChannel(ch);
            }
        }
    }

    @SuppressWarnings("deprecation")
    static void show(Context ctx, int id, String title, String body) {
        if (Build.VERSION.SDK_INT >= 33
                && ctx.checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ensureChannel(ctx);
        Intent open = new Intent(ctx, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(ctx, CHANNEL_ID) : new Notification.Builder(ctx);
        b.setSmallIcon(R.drawable.wf_ic_notifications)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setColor(BuildConfig.PRIMARY_COLOR)
                .setAutoCancel(true)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT < 26) b.setPriority(Notification.PRIORITY_HIGH);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(id, b.build());
    }
}
