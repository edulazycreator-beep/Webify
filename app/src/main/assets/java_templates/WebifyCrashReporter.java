package {{PACKAGE}};

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Perekam crash untuk build debug: stack trace disimpan ke filesDir, lalu pada peluncuran berikutnya
 * pengguna ditawari mengirim laporan ke Webify (bila terpasang) atau membagikannya.
 */
final class WebifyCrashReporter {

    private static final String TAG = "WebifyCrash";
    private static final String FILE = "webify_last_crash.txt";
    static final String WEBIFY_PACKAGE = "com.webify.app";
    static final String ACTION_REPORT = "com.webify.app.action.CRASH_REPORT";

    private WebifyCrashReporter() { }

    static void install(final Context context) {
        if (!BuildConfig.CRASH_REPORTER) return;
        final Context app = context.getApplicationContext();
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    write(app, t, e);
                } catch (Throwable ignored) {
                    // jangan pernah mengganggu crash asli
                }
                if (previous != null) previous.uncaughtException(t, e);
                else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    System.exit(10);
                }
            }
        });
    }

    private static void write(Context c, Thread t, Throwable e) throws Exception {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        String version = "?";
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = pi.versionName + " (" + pi.versionCode + ")";
        } catch (Exception ignored) { }
        pw.println("Aplikasi : " + c.getPackageName() + " v" + version);
        pw.println("Waktu    : " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        pw.println("Perangkat: " + Build.MANUFACTURER + " " + Build.MODEL + " • Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        pw.println("Thread   : " + t.getName());
        pw.println();
        e.printStackTrace(pw);
        pw.flush();
        FileOutputStream out = new FileOutputStream(new File(c.getFilesDir(), FILE));
        try {
            out.write(sw.toString().getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    /** Dipanggil dari MainActivity.onCreate: tampilkan dialog bila ada crash dari sesi sebelumnya. */
    static void offerPendingReport(final Activity activity) {
        if (!BuildConfig.CRASH_REPORTER) return;
        final File f = new File(activity.getFilesDir(), FILE);
        if (!f.exists()) return;
        final String report;
        try {
            byte[] buf = new byte[(int) Math.min(f.length(), 200_000)];
            FileInputStream in = new FileInputStream(f);
            int n;
            try { n = in.read(buf); } finally { in.close(); }
            report = new String(buf, 0, Math.max(n, 0), "UTF-8");
        } catch (Exception e) {
            f.delete();
            return;
        }
        f.delete();
        final boolean webifyInstalled = isWebifyInstalled(activity);
        AlertDialog.Builder b = new AlertDialog.Builder(activity)
                .setTitle("Aplikasi berhenti sebelumnya")
                .setMessage("Terjadi crash saat aplikasi terakhir dijalankan.\n\n" + firstLines(report, 6))
                .setNegativeButton("Tutup", null)
                .setNeutralButton("Bagikan", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) { share(activity, report); }
                });
        if (webifyInstalled) b.setPositiveButton("Kirim ke Webify", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) { sendToWebify(activity, report); }
        });
        b.show();
    }

    private static boolean isWebifyInstalled(Context c) {
        try {
            c.getPackageManager().getPackageInfo(WEBIFY_PACKAGE, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void sendToWebify(Activity a, String report) {
        try {
            Intent i = new Intent(ACTION_REPORT);
            i.setComponent(new ComponentName(WEBIFY_PACKAGE, WEBIFY_PACKAGE + ".CrashReportActivity"));
            i.putExtra(Intent.EXTRA_TEXT, report);
            i.putExtra("package", a.getPackageName());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            a.startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "Webify tidak menerima laporan", e);
            share(a, report);
        }
    }

    private static void share(Activity a, String report) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, "Crash " + a.getPackageName());
        i.putExtra(Intent.EXTRA_TEXT, report);
        try {
            a.startActivity(Intent.createChooser(i, "Bagikan laporan crash"));
        } catch (Exception ignored) { }
    }

    private static String firstLines(String s, int n) {
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length && i < n + 5; i++) {
            if (i < 5) continue; // lewati header
            sb.append(lines[i].trim()).append('\n');
        }
        return sb.toString().trim();
    }
}
