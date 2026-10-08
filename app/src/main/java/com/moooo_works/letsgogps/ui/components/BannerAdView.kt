package com.moooo_works.letsgogps.ui.components

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
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
    val initializer = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AdMobInitializerEntryPoint::class.java
        ).adMobInitializer()
    }
    val initializationState by initializer.state.collectAsStateWithLifecycle()

    val orientation = LocalConfiguration.current.orientation
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val widthDp = maxWidth.value.takeIf { it.isFinite() }?.toInt() ?: 0
        val adSize = remember(context, widthDp, orientation) {
            if (widthDp > 0) {
                // 保留地圖操作空間，使用較小的 anchored adaptive 尺寸。
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
            } else {
                null
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth().height(adSize?.height?.dp ?: 50.dp),
            contentAlignment = Alignment.Center
        ) {
            if (initializationState == AdMobInitializationState.Ready && adSize != null) {
                key(widthDp, orientation) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth(),
                        factory = { viewContext ->
                            AdView(viewContext).apply {
                                val request = BannerAdRequest.Builder(
                                    BuildConfig.BANNER_AD_UNIT_ID,
                                    adSize
                                ).build()
                                loadAd(request, object : AdLoadCallback<BannerAd> {
                                    override fun onAdLoaded(ad: BannerAd) {
                                        Log.d("BannerAdView", "Banner ad loaded")
                                        ad.adEventCallback = object : BannerAdEventCallback {
                                            override fun onAdImpression() {
                                                Log.d("BannerAdView", "Banner ad impression")
                                            }
                                        }
                                    }

                                    override fun onAdFailedToLoad(adError: LoadAdError) {
                                        Log.w("BannerAdView", "Banner ad load failed: ${adError.code}")
                                    }
                                })
                            }
                        },
                        // Every tab switch / ad-free toggle disposes this composable and creates a
                        // fresh AdView; without destroy() AdMob keeps the old WebView-backed view
                        // registered (refresh timers), leaking ~MBs per switch until OOM.
                        onRelease = { it.destroy() }
                    )
                }
            }
        }
    }
}
