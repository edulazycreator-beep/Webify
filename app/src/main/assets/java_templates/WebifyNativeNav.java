package {{PACKAGE}};

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Menu navigasi native tanpa AndroidX:
 *  - NAV_STYLE 1: Bottom Navigation Bar 56dp (ikon vektor + label, indikator warna aktif).
 *  - NAV_STYLE 2: Top bar + Navigation Drawer geser dari kiri dengan scrim & animasi.
 */
public class WebifyNativeNav {

    private final MainActivity activity;
    private final LinearLayout[] items;
    private final ImageView[] icons;
    private final TextView[] labels;
    private int active = -1;

    // drawer
    private FrameLayout drawerOverlay;
    private View scrim;
    private LinearLayout panel;
    private boolean drawerOpen = false;

    WebifyNativeNav(MainActivity activity) {
        this.activity = activity;
        int n = BuildConfig.NAV_LABELS.length;
        items = new LinearLayout[n];
        icons = new ImageView[n];
        labels = new TextView[n];
    }

    static boolean enabled() {
        return BuildConfig.NAV_STYLE != 0 && BuildConfig.NAV_LABELS.length > 0;
    }

    // ------------------------------------------------------------------ Bottom bar
    View createBottomBar() {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Color.WHITE);
        bar.setElevation(activity.dp(8));
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(activity);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setBackground(ripple(Color.TRANSPARENT));
            item.setClickable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { navigate(idx); }
            });
            ImageView icon = new ImageView(activity);
            icon.setImageResource(BuildConfig.NAV_ICONS[i]);
            item.addView(icon, new LinearLayout.LayoutParams(activity.dp(24), activity.dp(24)));
            TextView label = new TextView(activity);
            label.setText(BuildConfig.NAV_LABELS[i]);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            label.setSingleLine(true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
            llp.topMargin = activity.dp(2);
            item.addView(label, llp);
            bar.addView(item, new LinearLayout.LayoutParams(0, -1, 1f));
            items[i] = item;
            icons[i] = icon;
            labels[i] = label;
        }
        setActive(0);
        return bar;
    }

    // ------------------------------------------------------------------ Drawer
    View createTopBar() {
        LinearLayout top = new LinearLayout(activity);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(BuildConfig.STATUS_BAR_COLOR);
        top.setElevation(activity.dp(4));
        int fg = MainActivity.isLightColor(BuildConfig.STATUS_BAR_COLOR) ? Color.parseColor("#0F172A") : Color.WHITE;

        ImageView menu = new ImageView(activity);
        menu.setImageResource(R.drawable.wf_ic_menu);
        menu.setImageTintList(ColorStateList.valueOf(fg));
        menu.setScaleType(ImageView.ScaleType.CENTER);
        menu.setBackground(ripple(Color.TRANSPARENT));
        menu.setContentDescription("Menu");
        menu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openDrawer(); }
        });
        top.addView(menu, new LinearLayout.LayoutParams(activity.dp(56), activity.dp(56)));

        TextView title = new TextView(activity);
        title.setText(BuildConfig.APP_NAME);
        title.setTextColor(fg);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setSingleLine(true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        return top;
    }

    void attachDrawer(FrameLayout root) {
        drawerOverlay = new FrameLayout(activity);
        drawerOverlay.setVisibility(View.GONE);

        scrim = new View(activity);
        scrim.setBackgroundColor(Color.parseColor("#99000000"));
        scrim.setAlpha(0f);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { closeDrawer(); }
        });
        drawerOverlay.addView(scrim, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setElevation(activity.dp(16));
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));

        // Header: ikon + nama aplikasi
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(BuildConfig.PRIMARY_COLOR);
        header.setPadding(activity.dp(20), activity.dp(48), activity.dp(20), activity.dp(20));
        ImageView appIcon = new ImageView(activity);
        appIcon.setImageResource(R.mipmap.ic_launcher);
        header.addView(appIcon, new LinearLayout.LayoutParams(activity.dp(56), activity.dp(56)));
        TextView name = new TextView(activity);
        name.setText(BuildConfig.APP_NAME);
        name.setTextColor(MainActivity.isLightColor(BuildConfig.PRIMARY_COLOR) ? Color.parseColor("#0F172A") : Color.WHITE);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        name.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-2, -2);
        nlp.topMargin = activity.dp(12);
        header.addView(name, nlp);
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            LinearLayout row = drawerRow(BuildConfig.NAV_ICONS[i], BuildConfig.NAV_LABELS[i], new View.OnClickListener() {
                @Override public void onClick(View v) { closeDrawer(); navigate(idx); }
            });
            items[i] = row;
            icons[i] = (ImageView) row.getChildAt(0);
            labels[i] = (TextView) row.getChildAt(1);
            panel.addView(row);
        }

        if (BuildConfig.ABOUT_ENABLED) {
            View divider = new View(activity);
            divider.setBackgroundColor(Color.parseColor("#E2E8F0"));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-1, activity.dp(1));
            dlp.setMargins(0, activity.dp(8), 0, activity.dp(8));
            panel.addView(divider, dlp);
            panel.addView(drawerRow(R.drawable.wf_ic_star, "Beri Rating", new View.OnClickListener() {
                @Override public void onClick(View v) { closeDrawer(); WebifyAboutDialog.rateApp(activity); }
            }));
            panel.addView(drawerRow(R.drawable.wf_ic_info, "Tentang Aplikasi", new View.OnClickListener() {
                @Override public void onClick(View v) { closeDrawer(); WebifyAboutDialog.show(activity); }
            }));
        }

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(activity.dp(296), -1, Gravity.START);
        drawerOverlay.addView(scroll, plp);
        scroll.setTranslationX(-activity.dp(320));
        root.addView(drawerOverlay, new FrameLayout.LayoutParams(-1, -1));

        // Gesture geser dari tepi kiri untuk membuka
        View edge = new View(activity);
        edge.setOnTouchListener(new View.OnTouchListener() {
            float startX;
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (e.getActionMasked() == MotionEvent.ACTION_DOWN) { startX = e.getRawX(); return true; }
                if (e.getActionMasked() == MotionEvent.ACTION_MOVE && e.getRawX() - startX > activity.dp(48)) { openDrawer(); return true; }
                return e.getActionMasked() != MotionEvent.ACTION_UP;
            }
        });
        root.addView(edge, new FrameLayout.LayoutParams(activity.dp(16), -1, Gravity.START));
        setActive(0);
    }

    private LinearLayout drawerRow(int iconRes, String text, View.OnClickListener click) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(activity.dp(20), 0, activity.dp(20), 0);
        row.setMinimumHeight(activity.dp(52));
        row.setBackground(ripple(Color.TRANSPARENT));
        row.setClickable(true);
        row.setOnClickListener(click);
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconRes);
        icon.setImageTintList(ColorStateList.valueOf(Color.parseColor("#475569")));
        row.addView(icon, new LinearLayout.LayoutParams(activity.dp(24), activity.dp(24)));
        TextView label = new TextView(activity);
        label.setText(text);
        label.setTextColor(Color.parseColor("#0F172A"));
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-2, -2);
        llp.leftMargin = activity.dp(20);
        row.addView(label, llp);
        return row;
    }

    void openDrawer() {
        if (drawerOverlay == null || drawerOpen) return;
        drawerOpen = true;
        drawerOverlay.setVisibility(View.VISIBLE);
        drawerOverlay.bringToFront();
        scrim.animate().alpha(1f).setDuration(220).start();
        ((View) panel.getParent()).animate().translationX(0).setDuration(260)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(2f)).start();
    }

    void closeDrawer() {
        if (drawerOverlay == null || !drawerOpen) return;
        drawerOpen = false;
        scrim.animate().alpha(0f).setDuration(200).start();
        ((View) panel.getParent()).animate().translationX(-activity.dp(320)).setDuration(220)
                .withEndAction(new Runnable() { @Override public void run() { drawerOverlay.setVisibility(View.GONE); } }).start();
    }

    boolean isDrawerOpen() {
        return drawerOpen;
    }

    // ------------------------------------------------------------------ Status aktif
    private void navigate(int idx) {
        setActive(idx);
        activity.webView.loadUrl(BuildConfig.NAV_URLS[idx]);
    }

    private void setActive(int idx) {
        active = idx;
        int inactive = Color.parseColor("#64748B");
        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) continue;
            boolean on = i == idx;
            int c = on ? BuildConfig.PRIMARY_COLOR : inactive;
            if (BuildConfig.NAV_STYLE == 1) {
                icons[i].setImageTintList(ColorStateList.valueOf(c));
                labels[i].setTextColor(c);
                labels[i].setTypeface(null, on ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
            } else {
                GradientDrawable bg = new GradientDrawable();
                bg.setCornerRadius(activity.dp(12));
                bg.setColor(on ? (BuildConfig.PRIMARY_COLOR & 0x00FFFFFF) | 0x1F000000 : Color.TRANSPARENT);
                items[i].setBackground(new RippleDrawable(ColorStateList.valueOf(0x22000000), bg, null));
                icons[i].setImageTintList(ColorStateList.valueOf(on ? BuildConfig.PRIMARY_COLOR : Color.parseColor("#475569")));
                labels[i].setTextColor(on ? BuildConfig.PRIMARY_COLOR : Color.parseColor("#0F172A"));
            }
        }
    }

    /** Sinkronkan tab aktif dengan URL yang sedang dibuka (prefix terpanjang). */
    void syncWithUrl(String url) {
        if (url == null) return;
        String cur = normalize(url);
        int best = -1;
        int bestLen = -1;
        for (int i = 0; i < BuildConfig.NAV_URLS.length; i++) {
            String target = normalize(BuildConfig.NAV_URLS[i]);
            if ((cur.equals(target) || cur.startsWith(target + "/") || cur.startsWith(target + "?") || cur.startsWith(target + "#"))
                    && target.length() > bestLen) {
                best = i;
                bestLen = target.length();
            }
        }
        if (best >= 0 && best != active) setActive(best);
    }

    private static String normalize(String url) {
        try {
            Uri u = Uri.parse(url);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase();
            if (host.startsWith("www.")) host = host.substring(4);
            String path = u.getPath() == null ? "" : u.getPath();
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            return host + path;
        } catch (Exception e) {
            return url;
        }
    }

    private RippleDrawable ripple(int base) {
        return new RippleDrawable(ColorStateList.valueOf(0x22000000), new ColorDrawable(base), new ColorDrawable(Color.WHITE));
    }

    @SuppressWarnings("unused")
    private static void noop(ViewGroup g) { }
}
