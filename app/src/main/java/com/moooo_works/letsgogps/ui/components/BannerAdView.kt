package com.moooo_works.letsgogps.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    val initializer = remember(context.applicationContext) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AdMobInitializerEntryPoint::class.java
        ).adMobInitializer()
    }
    val initializationState by initializer.state.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
    ) {
        if (initializationState == AdMobInitializationState.Ready) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { viewContext ->
                    AdView(viewContext).apply {
                        val request = BannerAdRequest.Builder(
                            BuildConfig.BANNER_AD_UNIT_ID,
                            AdSize.BANNER
                        ).build()
                        loadAd(request, object : AdLoadCallback<BannerAd> {
                            override fun onAdLoaded(ad: BannerAd) = Unit

                            override fun onAdFailedToLoad(error: LoadAdError) = Unit
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
