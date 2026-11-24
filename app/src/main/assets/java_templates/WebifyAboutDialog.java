package {{PACKAGE}};

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Dialog native "Tentang Aplikasi" & aksi "Beri Rating". */
public class WebifyAboutDialog {

    static void show(final MainActivity a) {
        LinearLayout col = new LinearLayout(a);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setPadding(a.dp(24), a.dp(28), a.dp(24), a.dp(12));

        ImageView icon = new ImageView(a);
        icon.setImageResource(R.mipmap.ic_launcher);
        col.addView(icon, new LinearLayout.LayoutParams(a.dp(72), a.dp(72)));

        TextView name = new TextView(a);
        name.setText(BuildConfig.APP_NAME);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        name.setTextColor(Color.parseColor("#0F172A"));
        name.setGravity(Gravity.CENTER);
        name.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = a.dp(12);
        col.addView(name, nlp);

        TextView version = new TextView(a);
        version.setText("Versi " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        version.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        version.setTextColor(Color.parseColor("#64748B"));
        col.addView(version, new LinearLayout.LayoutParams(-2, -2));

        TextView pkg = new TextView(a);
        pkg.setText(BuildConfig.APPLICATION_ID);
        pkg.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        pkg.setTypeface(android.graphics.Typeface.MONOSPACE);
        pkg.setTextColor(Color.parseColor("#94A3B8"));
        col.addView(pkg, new LinearLayout.LayoutParams(-2, -2));

        final AlertDialog[] holder = new AlertDialog[1];
        if (BuildConfig.PRIVACY_URL.length() > 0) {
            col.addView(button(a, "Kebijakan Privasi", false, new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (holder[0] != null) holder[0].dismiss();
                    openUrl(a, BuildConfig.PRIVACY_URL);
                }
            }), buttonLp(a, 24));
        }
        col.addView(button(a, "Beri Rating di Play Store", true, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (holder[0] != null) holder[0].dismiss();
                rateApp(a);
            }
        }), buttonLp(a, BuildConfig.PRIVACY_URL.length() > 0 ? 10 : 24));

        holder[0] = new AlertDialog.Builder(a)
                .setView(col)
                .setPositiveButton("Tutup", null)
                .create();
        holder[0].show();
    }

    static void rateApp(MainActivity a) {
        String pkg = a.getPackageName();
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_NEW_DOCUMENT | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            openUrl(a, "https://play.google.com/store/apps/details?id=" + pkg);
        }
    }

    private static void openUrl(MainActivity a, String url) {
        try {
            a.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            a.webView.loadUrl(url);
        }
    }

    private static Button button(MainActivity a, String text, boolean filled, View.OnClickListener click) {
        Button b = new Button(a);
        b.setText(text);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(a.dp(12));
        if (filled) {
            bg.setColor(BuildConfig.PRIMARY_COLOR);
            b.setTextColor(MainActivity.isLightColor(BuildConfig.PRIMARY_COLOR) ? Color.BLACK : Color.WHITE);
        } else {
            bg.setColor(Color.TRANSPARENT);
            bg.setStroke(a.dp(1), Color.parseColor("#CBD5E1"));
            b.setTextColor(BuildConfig.PRIMARY_COLOR);
        }
        b.setBackground(bg);
        b.setOnClickListener(click);
        return b;
    }

    private static LinearLayout.LayoutParams buttonLp(MainActivity a, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, a.dp(48));
        lp.topMargin = a.dp(topDp);
        return lp;
    }
}
