package {{PACKAGE}};

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.Executor;

/**
 * Kunci biometrik native (android.hardware.biometrics.BiometricPrompt, API 28+).
 * API 30+ : BIOMETRIC_WEAK | DEVICE_CREDENTIAL
 * API 29  : setDeviceCredentialAllowed(true)
 * API 28  : biometrik + tombol negatif
 * API < 28: fallback KeyguardManager (PIN/pola) bila tersedia.
 */
public class BiometricLockHelper {

    static final int REQ_DEVICE_CREDENTIAL = 4109;
    private static final long RELOCK_AFTER_MS = 30_000L;

    private final MainActivity activity;
    private FrameLayout overlay;
    private boolean unlocked = false;
    private long backgroundAt = 0L;
    private boolean prompting = false;

    BiometricLockHelper(MainActivity activity) {
        this.activity = activity;
    }

    static boolean enabled() {
        return BuildConfig.BIOMETRIC_LOCK;
    }

    /** Pasang layar kunci di atas konten & langsung minta autentikasi. */
    void lock(FrameLayout root) {
        if (!isSecureAvailable()) {
            unlocked = true; // tidak ada biometrik/PIN di perangkat — jangan kunci pengguna keluar
            return;
        }
        unlocked = false;
        if (overlay == null) {
            overlay = buildOverlay();
            root.addView(overlay, new FrameLayout.LayoutParams(-1, -1));
        }
        overlay.setVisibility(View.VISIBLE);
        overlay.setAlpha(1f);
        overlay.bringToFront();
        authenticate();
    }

    void onStop() {
        if (unlocked) backgroundAt = System.currentTimeMillis();
    }

    void onStart(FrameLayout root) {
        if (unlocked && backgroundAt > 0 && System.currentTimeMillis() - backgroundAt > RELOCK_AFTER_MS) lock(root);
    }

    boolean isLocked() {
        return overlay != null && !unlocked;
    }

    private boolean isSecureAvailable() {
        try {
            KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
            boolean deviceSecure = km != null && km.isDeviceSecure();
            if (Build.VERSION.SDK_INT >= 30) {
                BiometricManager bm = activity.getSystemService(BiometricManager.class);
                int r = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                return r == BiometricManager.BIOMETRIC_SUCCESS || deviceSecure;
            }
            return deviceSecure;
        } catch (Exception e) {
            return false;
        }
    }

    private void authenticate() {
        if (prompting) return;
        prompting = true;
        try {
            if (Build.VERSION.SDK_INT >= 28) {
                Executor executor = activity.getMainExecutor();
                BiometricPrompt.Builder b = new BiometricPrompt.Builder(activity)
                        .setTitle("Buka " + BuildConfig.APP_NAME)
                        .setSubtitle("Verifikasi identitas untuk melanjutkan");
                if (Build.VERSION.SDK_INT >= 30) {
                    b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL);
                } else if (Build.VERSION.SDK_INT == 29) {
                    b.setDeviceCredentialAllowed(true);
                } else {
                    b.setNegativeButton("Gunakan PIN", executor, new android.content.DialogInterface.OnClickListener() {
                        @Override public void onClick(android.content.DialogInterface d, int which) {
                            prompting = false;
                            confirmDeviceCredential();
                        }
                    });
                }
                b.build().authenticate(new CancellationSignal(), executor, new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        prompting = false;
                        unlock();
                    }
                    @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                        prompting = false;
                    }
                });
            } else {
                prompting = false;
                confirmDeviceCredential();
            }
        } catch (Exception e) {
            prompting = false;
            confirmDeviceCredential();
        }
    }

    @SuppressWarnings("deprecation")
    private void confirmDeviceCredential() {
        KeyguardManager km = (KeyguardManager) activity.getSystemService(Context.KEYGUARD_SERVICE);
        Intent i = km == null ? null : km.createConfirmDeviceCredentialIntent("Buka " + BuildConfig.APP_NAME, "Masukkan PIN, pola, atau kata sandi");
        if (i != null) activity.startActivityForResult(i, REQ_DEVICE_CREDENTIAL);
        else unlock();
    }

    void onActivityResult(int requestCode, int resultCode) {
        if (requestCode == REQ_DEVICE_CREDENTIAL && resultCode == android.app.Activity.RESULT_OK) unlock();
    }

    private void unlock() {
        unlocked = true;
        if (overlay != null) {
            overlay.animate().alpha(0f).setDuration(250).withEndAction(new Runnable() {
                @Override public void run() { overlay.setVisibility(View.GONE); }
            }).start();
        }
    }

    private FrameLayout buildOverlay() {
        FrameLayout f = new FrameLayout(activity);
        f.setBackgroundColor(BuildConfig.SPLASH_BG_COLOR);
        f.setClickable(true);
        boolean light = MainActivity.isLightColor(BuildConfig.SPLASH_BG_COLOR);
        int fg = light ? Color.parseColor("#0F172A") : Color.WHITE;

        LinearLayout col = new LinearLayout(activity);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(activity);
        icon.setImageResource(R.mipmap.ic_launcher);
        col.addView(icon, new LinearLayout.LayoutParams(activity.dp(88), activity.dp(88)));

        ImageView lock = new ImageView(activity);
        lock.setImageResource(R.drawable.wf_ic_lock);
        lock.setImageTintList(ColorStateList.valueOf(BuildConfig.PRIMARY_COLOR));
        LinearLayout.LayoutParams lockLp = new LinearLayout.LayoutParams(activity.dp(28), activity.dp(28));
        lockLp.topMargin = activity.dp(24);
        col.addView(lock, lockLp);

        TextView title = new TextView(activity);
        title.setText(BuildConfig.APP_NAME + " terkunci");
        title.setTextColor(fg);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = activity.dp(12);
        col.addView(title, tlp);

        TextView sub = new TextView(activity);
        sub.setText("Gunakan sidik jari, wajah, atau PIN perangkat");
        sub.setTextColor(light ? Color.parseColor("#475569") : Color.parseColor("#CBD5E1"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        sub.setGravity(Gravity.CENTER);
        col.addView(sub, new LinearLayout.LayoutParams(-2, -2));

        Button retry = new Button(activity);
        retry.setText("Buka Kunci Ulang");
        retry.setAllCaps(false);
        retry.setTextColor(MainActivity.isLightColor(BuildConfig.PRIMARY_COLOR) ? Color.BLACK : Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BuildConfig.PRIMARY_COLOR);
        bg.setCornerRadius(activity.dp(12));
        retry.setBackground(bg);
        retry.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { authenticate(); }
        });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(activity.dp(220), activity.dp(50));
        blp.topMargin = activity.dp(32);
        col.addView(retry, blp);

        f.addView(col, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        return f;
    }
}
