package {{PACKAGE}};

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Overlay splash: ikon aplikasi, nama, dan loader opsional; di-fade-out oleh MainActivity. */
public class WebifySplashView extends FrameLayout {

    public WebifySplashView(Context context, int bgColor, boolean showLoader, int accentColor) {
        super(context);
        setBackgroundColor(bgColor);
        setClickable(true);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        ImageView icon = new ImageView(context);
        icon.setImageResource(R.mipmap.ic_launcher);
        int size = (int) (96 * d);
        column.addView(icon, new LinearLayout.LayoutParams(size, size));

        TextView title = new TextView(context);
        title.setText(R.string.app_name);
        title.setTextSize(20);
        title.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        title.setTextColor(MainActivity.isLightColor(bgColor) ? Color.parseColor("#0F172A") : Color.WHITE);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(-2, -2);
        tlp.topMargin = (int) (16 * d);
        column.addView(title, tlp);

        if (showLoader) {
            ProgressBar loader = new ProgressBar(context);
            loader.setIndeterminate(true);
            loader.setIndeterminateTintList(ColorStateList.valueOf(
                    MainActivity.isLightColor(bgColor) ? accentColor : Color.WHITE));
            int ls = (int) (28 * d);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(ls, ls);
            llp.topMargin = (int) (32 * d);
            column.addView(loader, llp);
        }

        addView(column, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true; // blokir sentuhan ke WebView selama splash
    }
}
