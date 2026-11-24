package {{PACKAGE}};

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

/**
 * Pull-to-refresh ringan (pengganti SwipeRefreshLayout tanpa dependensi AndroidX).
 */
public class WebifyRefreshLayout extends FrameLayout {

    public interface OnRefreshListener {
        void onRefresh();
    }

    private final WebView webView;
    private final FrameLayout indicator;
    private final ProgressBar spinner;
    private final int touchSlop;
    private final float density;
    private OnRefreshListener listener;
    private boolean enabledPull = true;
    private boolean refreshing = false;
    private boolean dragging = false;
    private float startY;
    private float startX;
    private float dragDistance;

    public WebifyRefreshLayout(Context context, WebView webView, int color) {
        super(context);
        this.webView = webView;
        this.density = getResources().getDisplayMetrics().density;
        this.touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        addView(webView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        indicator = new FrameLayout(context);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.WHITE);
        indicator.setBackground(bg);
        indicator.setElevation(6 * density);
        spinner = new ProgressBar(context);
        spinner.setIndeterminate(true);
        spinner.setIndeterminateTintList(ColorStateList.valueOf(color));
        int pad = (int) (8 * density);
        LayoutParams slp = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        slp.setMargins(pad, pad, pad, pad);
        indicator.addView(spinner, slp);
        int size = (int) (40 * density);
        LayoutParams ilp = new LayoutParams(size, size, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        addView(indicator, ilp);
        indicator.setTranslationY(-size * 1.5f);
        indicator.setAlpha(0f);
    }

    public void setOnRefreshListener(OnRefreshListener l) {
        listener = l;
    }

    public void setEnabledPull(boolean enabled) {
        enabledPull = enabled;
    }

    private float threshold() {
        return 80 * density;
    }

    private boolean canChildScrollUp() {
        return webView.getScrollY() > 0 || webView.canScrollVertically(-1);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!enabledPull || refreshing) return false;
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                startY = ev.getY();
                startX = ev.getX();
                dragging = false;
                break;
            case MotionEvent.ACTION_MOVE:
                float dy = ev.getY() - startY;
                float dx = Math.abs(ev.getX() - startX);
                if (!dragging && dy > touchSlop * 2 && dy > dx * 1.5f && !canChildScrollUp()) {
                    dragging = true;
                    startY = ev.getY();
                    return true;
                }
                break;
            default:
                dragging = false;
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!dragging) return super.onTouchEvent(ev);
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                dragDistance = Math.max(0, ev.getY() - startY);
                float damped = (float) (threshold() * 1.6f * (1 - Math.exp(-dragDistance / (threshold() * 1.6f))));
                indicator.setTranslationY(damped - indicator.getHeight());
                indicator.setAlpha(Math.min(1f, dragDistance / threshold()));
                indicator.setRotation(dragDistance);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                if (dragDistance >= threshold() && ev.getActionMasked() == MotionEvent.ACTION_UP) {
                    setRefreshing(true);
                    if (listener != null) listener.onRefresh();
                } else {
                    hideIndicator();
                }
                dragDistance = 0;
                return true;
            default:
                return true;
        }
    }

    public void setRefreshing(boolean value) {
        refreshing = value;
        if (value) {
            indicator.animate().translationY(16 * density).alpha(1f).rotation(0).setDuration(150).start();
        } else {
            hideIndicator();
        }
    }

    private void hideIndicator() {
        indicator.animate().translationY(-indicator.getHeight() * 1.5f).alpha(0f).setDuration(200).start();
    }
}
