package com.moooo_works.letsgogps.ui.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.moooo_works.letsgogps.BuildConfig
import com.moooo_works.letsgogps.MainApplication

private const val TAG = "MockGPS/InterstitialAd"
private const val MAX_RETRY = 3
private const val RETRY_BASE_DELAY_MS = 2000L

class InterstitialAdManager(private val context: Context) {

    private var interstitialAd: InterstitialAd? = null
    private var retryCount = 0
    private val handler = Handler(Looper.getMainLooper())

    init {
        if (MainApplication.isMobileAdsInitialized) {
            loadAd()
        } else {
            // SDK 尚未初始化，延遲後嘗試（使用退避起始值）
            handler.postDelayed({ loadAd() }, RETRY_BASE_DELAY_MS)
        }
    }

    fun loadAd() {
        if (!MainApplication.isMobileAdsInitialized) return
        InterstitialAd.load(
            context,
            BuildConfig.INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    retryCount = 0
                    Log.d(TAG, "Ad loaded")
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitialAd = null
                    Log.w(TAG, "Ad failed to load (attempt $retryCount/$MAX_RETRY): ${error.message}")
                    if (retryCount < MAX_RETRY) {
                        val delay = RETRY_BASE_DELAY_MS * (1L shl retryCount) // 2s, 4s, 8s
                        retryCount++
                        handler.postDelayed({ loadAd() }, delay)
                    } else {
                        Log.w(TAG, "Ad max retries reached, giving up until next showAd call")
                    }
                }
            }
        )
    }

    fun showAd(activity: Activity, onComplete: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            // 廣告未就緒，不阻擋使用者操作，重置計數並預載
            onComplete()
            retryCount = 0
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
                Log.w(TAG, "Ad failed to show: ${error.message}")
                interstitialAd = null
                loadAd()
                onComplete()
            }
        }
        ad.show(activity)
    }
}
