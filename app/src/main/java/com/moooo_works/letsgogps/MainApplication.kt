package com.moooo_works.letsgogps

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MainApplication : Application() {

    companion object {
        var isMobileAdsInitialized = false
            private set
    }

    override fun onCreate() {
        super.onCreate()
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("3E51381E6BA58281AEEC253ACF8F7529"))
                .build()
        )
        MobileAds.initialize(this) { isMobileAdsInitialized = true }
    }
}
