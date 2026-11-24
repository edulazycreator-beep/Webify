package {{PACKAGE}};

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.initialization.InitializationStatus;
import com.google.android.gms.ads.initialization.OnInitializationCompleteListener;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;

/** Integrasi Google AdMob: banner adaptif native di bawah WebView + interstitial via JS Bridge. */
final class WebifyAdManager {

    private static final String TAG = "WebifyAds";
    private static AdView banner;
    private static InterstitialAd interstitial;
    private static MainActivity host;

    private WebifyAdManager() {}

    static void init(final MainActivity a) {
        host = a;
        MobileAds.initialize(a, new OnInitializationCompleteListener() {
            @Override public void onInitializationComplete(InitializationStatus status) {
                loadInterstitial();
            }
        });
    }

    static View createBanner(MainActivity a) {
        if (BuildConfig.ADMOB_BANNER_ID.length() == 0) return null;
        banner = new AdView(a);
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        int widthDp = (int) (dm.widthPixels / dm.density);
        banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(a, widthDp));
        banner.setAdUnitId(BuildConfig.ADMOB_BANNER_ID);
        banner.loadAd(new AdRequest.Builder().build());
        return banner;
    }

    private static void loadInterstitial() {
        if (host == null || BuildConfig.ADMOB_INTERSTITIAL_ID.length() == 0) return;
        InterstitialAd.load(host, BuildConfig.ADMOB_INTERSTITIAL_ID, new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(InterstitialAd ad) {
                interstitial = ad;
                ad.setFullScreenContentCallback(new FullScreenContentCallback() {
                    @Override public void onAdDismissedFullScreenContent() {
                        interstitial = null;
                        loadInterstitial();
                    }
                });
            }

            @Override public void onAdFailedToLoad(LoadAdError error) {
                Log.w(TAG, "Interstitial gagal dimuat: " + error.getMessage());
                interstitial = null;
            }
        });
    }

    static boolean showInterstitial(MainActivity a) {
        if (interstitial == null) {
            loadInterstitial();
            return false;
        }
        interstitial.show(a);
        return true;
    }

    static void destroy() {
        if (banner != null) banner.destroy();
        banner = null;
        interstitial = null;
        host = null;
    }
}
