package com.example.mockgps.data.engine

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import com.example.mockgps.domain.LocationMockEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidLocationMockEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationMockEngine {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providerName = LocationManager.GPS_PROVIDER

    override fun setupMockProvider() {
        try {
            locationManager.addTestProvider(
                providerName,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                0,
                5
            )
            locationManager.setTestProviderEnabled(providerName, true)
        } catch (e: SecurityException) {
            throw e
        } catch (e: IllegalArgumentException) {
            // Provider might already exist, which is fine.
            // Ensure it is enabled.
            try {
                locationManager.setTestProviderEnabled(providerName, true)
            } catch (ignored: Exception) {}
        }
    }

    override fun teardownMockProvider() {
        try {
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {
            // Ignore
        }
    }

    override fun setLocation(latitude: Double, longitude: Double) {
        val mockLocation = Location(providerName)
        mockLocation.latitude = latitude
        mockLocation.longitude = longitude
        mockLocation.altitude = 0.0
        mockLocation.time = System.currentTimeMillis()
        mockLocation.speed = 0.0f
        mockLocation.bearing = 0.0f
        mockLocation.accuracy = 5.0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mockLocation.elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }

        try {
            locationManager.setTestProviderLocation(providerName, mockLocation)
        } catch (e: Exception) {
            // Log or handle
        }
    }

    override fun isMockingAllowed(): Boolean {
         return try {
             // Try to add a dummy provider to check permission
             val testProvider = "test_check_permission"
             locationManager.addTestProvider(
                testProvider,
                false, false, false, false, true, true, true, 0, 5
            )
            locationManager.removeTestProvider(testProvider)
            true
         } catch (e: SecurityException) {
             false
         } catch (e: Exception) {
             // Other exceptions (e.g. IllegalArgument if exists) might imply we have access,
             // but let's be safe.
             false
         }
    }
}
