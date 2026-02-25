package com.example.mockgps.data.engine

import android.location.provider.ProviderProperties
import android.content.Context
import android.location.LocationManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import java.lang.SecurityException

class AndroidLocationMockEngineTest {
    private val context = mockk<Context>()
    private val locationManager = mockk<LocationManager>(relaxed = true)

    @Before
    fun setup() {
        every { context.getSystemService(Context.LOCATION_SERVICE) } returns locationManager
    }

    @Test
    fun `setupMockProvider calls addTestProvider`() {
        val engine = AndroidLocationMockEngine(context)

        engine.setupMockProvider()

        verify {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true, ProviderProperties.POWER_USAGE_LOW, 5
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        }
    }

    @Test(expected = SecurityException::class)
    fun `setupMockProvider throws SecurityException if denied`() {
        every {
            locationManager.addTestProvider(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } throws SecurityException("Denied")

        val engine = AndroidLocationMockEngine(context)
        engine.setupMockProvider()
    }
}
