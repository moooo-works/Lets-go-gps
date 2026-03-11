package com.moooo_works.letsgogps.ui.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.moooo_works.letsgogps.MainApplication

private const val TAG = "MockGPS/InterstitialAd"
// TODO: 上架前換回真實 ID: ca-app-pub-8495982996587452/7493536385
private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

class InterstitialAdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null

    init {
        if (MainApplication.isMobileAdsInitialized) loadAd()
        else android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ loadAd() }, 2000)
    }

    fun loadAd() {
        if (!MainApplication.isMobileAdsInitialized) return
        InterstitialAd.load(
            context,
            INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    Log.d(TAG, "Ad loaded")
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.w(TAG, "Ad failed to load: ${error.message}")
                }
            }
        )
    }

    fun showAd(activity: Activity, onComplete: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            onComplete()
            loadAd()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                loadAd()
                onComplete()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                loadAd()
                onComplete()
            }
        }
        ad.show(activity)
    }
}
