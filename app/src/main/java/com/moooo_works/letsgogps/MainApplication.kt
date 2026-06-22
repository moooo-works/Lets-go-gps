package com.moooo_works.letsgogps

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.moooo_works.letsgogps.service.MockSessionRestorer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class MainApplication : Application() {

    companion object {
        var isMobileAdsInitialized = false
            private set
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RestorerEntryPoint {
        fun mockSessionRestorer(): MockSessionRestorer
    }

    override fun onCreate() {
        super.onCreate()
        MobileAds.setRequestConfiguration(
            RequestConfiguration.Builder()
                .setTestDeviceIds(listOf("3E51381E6BA58281AEEC253ACF8F7529"))
                .build()
        )
        MobileAds.initialize(this) { isMobileAdsInitialized = true }

        // Recover an interrupted mock session if the process was killed mid-use.
        EntryPointAccessors.fromApplication(this, RestorerEntryPoint::class.java)
            .mockSessionRestorer()
            .register()
    }
}
