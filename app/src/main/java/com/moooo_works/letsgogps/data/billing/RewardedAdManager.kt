package com.moooo_works.letsgogps.data.billing

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback
import com.moooo_works.letsgogps.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardedAdManager(
    private val loader: RewardedAdLoader,
    private val unitId: String,
    private val initializationGate: AdMobInitializationGate
) {

    @Inject
    constructor(
        adMobInitializer: AdMobInitializer
    ) : this(
        loader = AdMobRewardedAdLoader(),
        unitId = BuildConfig.REWARDED_AD_UNIT_ID,
        initializationGate = adMobInitializer
    )

    private var loadedAd: LoadedAd? = null
    private var isLoading = false

    fun preload() {
        if (isLoading || loadedAd != null) return
        isLoading = true
        initializationGate.whenReady { ready ->
            if (!ready) {
                isLoading = false
                return@whenReady
            }
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
    }

    fun showAd(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit) {
        val ad = loadedAd
        if (ad == null) {
            onUnavailable()
            preload()
            return
        }
        loadedAd = null
        val rewardDelivered = AtomicBoolean(false)
        ad.show(
            activity = activity,
            onReward = {
                if (rewardDelivered.compareAndSet(false, true)) {
                    onReward()
                }
            },
            onDismiss = {
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

    private class AdMobRewardedAdLoader : RewardedAdLoader {
        private val mainHandler = Handler(Looper.getMainLooper())

        override fun load(unitId: String, onLoaded: (LoadedAd) -> Unit, onFailed: () -> Unit) {
            val request = AdRequest.Builder(unitId).build()
            RewardedAd.load(request, object : AdLoadCallback<RewardedAd> {
                override fun onAdLoaded(ad: RewardedAd) {
                    mainHandler.post { onLoaded(AdMobLoadedAd(ad, mainHandler)) }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    mainHandler.post(onFailed)
                }
            })
        }
    }

    private class AdMobLoadedAd(
        private val ad: RewardedAd,
        private val mainHandler: Handler
    ) : LoadedAd {
        override fun show(activity: Activity, onReward: () -> Unit, onDismiss: () -> Unit) {
            val completed = AtomicBoolean(false)
            ad.adEventCallback = object : RewardedAdEventCallback {
                override fun onAdDismissedFullScreenContent() {
                    completeOnce(completed, onDismiss)
                }

                override fun onAdFailedToShowFullScreenContent(error: FullScreenContentError) {
                    completeOnce(completed, onDismiss)
                }
            }
            ad.show(activity) {
                mainHandler.post(onReward)
            }
        }

        private fun completeOnce(completed: AtomicBoolean, onDismiss: () -> Unit) {
            if (!completed.compareAndSet(false, true)) return
            mainHandler.post {
                ad.destroy()
                onDismiss()
            }
        }
    }
}
