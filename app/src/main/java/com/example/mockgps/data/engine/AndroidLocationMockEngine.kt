package com.example.mockgps.data.engine

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.mockgps.domain.LocationMockEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

sealed class MockEngineError {
    data class Setup(val cause: Throwable) : MockEngineError()
    data class Teardown(val cause: Throwable) : MockEngineError()
    data class SetLocation(val cause: Throwable) : MockEngineError()
    data class PermissionCheck(val cause: Throwable) : MockEngineError()
}

class AndroidLocationMockEngine @Inject constructor(
    @ApplicationContext private val context: Context
) : LocationMockEngine {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val providerName = LocationManager.GPS_PROVIDER

    private val _errors = MutableSharedFlow<MockEngineError>(extraBufferCapacity = ERROR_BUFFER_CAPACITY)
    val errors: SharedFlow<MockEngineError> = _errors.asSharedFlow()

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
            reportError(MockEngineError.Setup(e), "setupMockProvider security exception")
            throw e
        } catch (e: IllegalArgumentException) {
            try {
                locationManager.setTestProviderEnabled(providerName, true)
            } catch (enableError: Exception) {
                reportError(MockEngineError.Setup(enableError), "setupMockProvider failed to enable existing provider")
            }
        }
    }

    override fun teardownMockProvider() {
        try {
            locationManager.removeTestProvider(providerName)
        } catch (e: Exception) {
            reportError(MockEngineError.Teardown(e), "teardownMockProvider failed")
        }
    }

    override fun setLocation(latitude: Double, longitude: Double) {
        try {
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
            locationManager.setTestProviderLocation(providerName, mockLocation)
        } catch (e: Exception) {
            reportError(MockEngineError.SetLocation(e), "setLocation failed")
        }
    }

    override fun isMockingAllowed(): Boolean {
        return try {
            val testProvider = "test_check_permission"
            locationManager.addTestProvider(
                testProvider,
                false,
                false,
                false,
                false,
                true,
                true,
                true,
                ProviderProperties.POWER_USAGE_LOW,
                5
            )
            locationManager.removeTestProvider(testProvider)
            true
        } catch (e: SecurityException) {
            reportError(MockEngineError.PermissionCheck(e), "isMockingAllowed denied")
            false
        } catch (e: Exception) {
            reportError(MockEngineError.PermissionCheck(e), "isMockingAllowed failed")
            false
        }
    }

    private fun reportError(error: MockEngineError, message: String) {
        runCatching {
            Log.e(TAG, message, error.cause())
        }
        _errors.tryEmit(error)
    }

    private fun MockEngineError.cause(): Throwable {
        return when (this) {
            is MockEngineError.Setup -> cause
            is MockEngineError.Teardown -> cause
            is MockEngineError.SetLocation -> cause
            is MockEngineError.PermissionCheck -> cause
        }
    }

    private companion object {
        private const val TAG = "AndroidLocationMockEngine"
        private const val ERROR_BUFFER_CAPACITY = 16
    }
}
