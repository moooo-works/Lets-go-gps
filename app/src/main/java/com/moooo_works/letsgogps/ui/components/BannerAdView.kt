package com.moooo_works.letsgogps.ui.components

import android.util.Log
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.moooo_works.letsgogps.BuildConfig
import com.moooo_works.letsgogps.data.billing.AdMobInitializationState
import com.moooo_works.letsgogps.data.billing.AdMobInitializerEntryPoint
import dagger.hilt.android.EntryPointAccessors

@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AdMobInitializerEntryPoint::class.java
        )
    }
    val initializer = entryPoint.adMobInitializer()
    val consentManager = entryPoint.adConsentManager()
    val consentState by consentManager.state.collectAsStateWithLifecycle()
    val initializationState by initializer.state.collectAsStateWithLifecycle()
    val activity = remember(context) { context.findAdActivity() }

    val orientation = LocalConfiguration.current.orientation
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.takeIf { it.isFinite() }?.toInt() ?: 0
        val adSize = remember(context, widthDp, orientation) {
            if (widthDp > 0) AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp) else null
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(adSize?.height?.dp ?: 50.dp),
            contentAlignment = Alignment.Center
        ) {
            if (initializationState == AdMobInitializationState.Ready && consentState.canRequestAds &&
                activity != null && adSize != null) {
                key(consentState.revision, widthDp, orientation) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = {
                            AdView(activity).apply {
                                val request = BannerAdRequest.Builder(BuildConfig.BANNER_AD_UNIT_ID, adSize).build()
                                if (consentManager.prepareAdRequest() &&
                                    consentManager.state.value.revision == consentState.revision &&
                                    !activity.isFinishing && !activity.isDestroyed) {
                                    loadAd(request, object : AdLoadCallback<BannerAd> {
                                        override fun onAdLoaded(ad: BannerAd) {
                                            Log.d("BannerAdView", "Banner ad loaded")
                                            ad.adEventCallback = object : BannerAdEventCallback {
                                                override fun onAdImpression() { Log.d("BannerAdView", "Banner ad impression") }
                                            }
                                        }
                                        override fun onAdFailedToLoad(error: LoadAdError) {
                                            Log.w("BannerAdView", "Banner ad load failed: ${error.code}")
                                        }
                                    })
                                }
                            }
                        },
                        // 寬度、方向或同意變更時，釋放 SDK 視窗。
                        onRelease = { it.destroy() }
                    )
                }
            }
        }
    }
}

internal fun Context.findAdActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> if (baseContext !== this) baseContext.findAdActivity() else null
    else -> null
}
