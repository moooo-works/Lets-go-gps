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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardedAdManager(
    private val loader: RewardedAdLoader,
    private val unitId: String,
    private val initializationGate: AdMobInitializationGate,
    private val consent: AdConsentGate,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {

    @Inject
    constructor(
        adMobInitializer: AdMobInitializer,
        adConsentManager: AdConsentManager
    ) : this(
        loader = AdMobRewardedAdLoader(),
        unitId = BuildConfig.REWARDED_AD_UNIT_ID,
        initializationGate = adMobInitializer,
        consent = adConsentManager
    )

    private var loadedAd: LoadedAd? = null
    private var isLoading = false
    private var generation = 0L
    private var consentRevision = consent.state.value.revision

    init {
        scope.launch {
            consent.state.collect { state ->
                if (!state.canRequestAds || state.revision != consentRevision) {
                    invalidate()
                }
                consentRevision = state.revision
            }
        }
    }

    private fun invalidate() {
        generation++
        loadedAd?.destroy()
        loadedAd = null
        isLoading = false
    }

    fun preload() {
        refreshConsent()
        if (!consent.state.value.canRequestAds) return
        if (isLoading || loadedAd != null) return
        isLoading = true
        val token = generation
        val revision = consent.state.value.revision
        fun current() = token == generation && revision == consent.state.value.revision &&
            consent.state.value.canRequestAds
        initializationGate.whenReady { ready ->
            if (!current()) return@whenReady
            if (!ready) {
                isLoading = false
                return@whenReady
            }
            loader.load(
                unitId = unitId,
                onLoaded = { ad ->
                    if (current()) {
                        loadedAd = ad
                        isLoading = false
                    } else ad.destroy()
                },
                onFailed = {
                    if (current()) {
                        loadedAd = null
                        isLoading = false
                    }
                }
            )
        }
    }

    private fun refreshConsent() {
        val state = consent.state.value
        if (!state.canRequestAds || state.revision != consentRevision) invalidate()
        consentRevision = state.revision
    }

    fun showAd(activity: Activity, onReward: () -> Unit, onUnavailable: () -> Unit) {
        refreshConsent()
        if (!consent.state.value.canRequestAds) {
            onUnavailable()
            return
        }
        val ad = loadedAd
        if (ad == null) {
            onUnavailable()
            preload()
            return
        }
        loadedAd = null
        val revision = consent.state.value.revision
        val rewardDelivered = AtomicBoolean(false)
        ad.show(
            activity = activity,
            onReward = {
                if (consent.state.value.canRequestAds && consent.state.value.revision == revision &&
                    rewardDelivered.compareAndSet(false, true)) {
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
        fun destroy()
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
        override fun destroy() { ad.destroy() }

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
