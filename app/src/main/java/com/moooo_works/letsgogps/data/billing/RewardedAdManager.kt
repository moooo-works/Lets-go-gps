package com.moooo_works.letsgogps.data.billing

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.moooo_works.letsgogps.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardedAdManager(
    private val loader: RewardedAdLoader,
    private val unitId: String
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        loader = AdMobRewardedAdLoader(context),
        unitId = BuildConfig.REWARDED_AD_UNIT_ID
    )

    private var loadedAd: LoadedAd? = null
    private var isLoading = false

    fun preload() {
        if (isLoading || loadedAd != null) return
        isLoading = true
        loader.load(
            unitId = unitId,
            onLoaded = { ad ->
                loadedAd = ad
                isLoading = false
            },
            onFailed = {
                loadedAd = null
                isLoading = false
            }
        )
    }

    fun showAd(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit) {
        val ad = loadedAd
        if (ad == null) {
            onUnavailable()
            preload()
            return
        }
        loadedAd = null
        var rewarded = false
        ad.show(
            activity = activity,
            onReward = { rewarded = true },
            onDismiss = {
                if (rewarded) onReward()
                preload()
            }
        )
    }

    interface RewardedAdLoader {
        fun load(unitId: String, onLoaded: (LoadedAd) -> Unit, onFailed: () -> Unit)
    }

    interface LoadedAd {
        fun show(activity: Activity, onReward: () -> Unit, onDismiss: () -> Unit)
    }

    private class AdMobRewardedAdLoader(private val context: Context) : RewardedAdLoader {
        override fun load(unitId: String, onLoaded: (LoadedAd) -> Unit, onFailed: () -> Unit) {
            RewardedAd.load(
                context,
                unitId,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        onLoaded(AdMobLoadedAd(ad))
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        onFailed()
                    }
                }
            )
        }
    }

    private class AdMobLoadedAd(private val ad: RewardedAd) : LoadedAd {
        override fun show(activity: Activity, onReward: () -> Unit, onDismiss: () -> Unit) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() { onDismiss() }
            }
            ad.show(activity, OnUserEarnedRewardListener { _ -> onReward() })
        }
    }
}
