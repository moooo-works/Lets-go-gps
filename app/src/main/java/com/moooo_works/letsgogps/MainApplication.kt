package com.moooo_works.letsgogps

import android.app.Application
import com.moooo_works.letsgogps.service.MockSessionRestorer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent

@HiltAndroidApp
class MainApplication : Application() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RestorerEntryPoint {
        fun mockSessionRestorer(): MockSessionRestorer
    }

    override fun onCreate() {
        super.onCreate()
        // Recover an interrupted mock session if the process was killed mid-use.
        EntryPointAccessors.fromApplication(this, RestorerEntryPoint::class.java)
            .mockSessionRestorer()
            .register()
    }
}
