package com.example.mockgps.data.engine

import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.example.mockgps.domain.LocationMockEngine
import com.example.mockgps.domain.MockPermissionStatus
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
    private val appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    private val mockProviders = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)

    private val _errors = MutableSharedFlow<MockEngineError>(extraBufferCapacity = ERROR_BUFFER_CAPACITY)
    val errors: SharedFlow<MockEngineError> = _errors.asSharedFlow()

    override fun setupMockProvider() {
        mockProviders.forEach { provider ->
            ensureProviderEnabled(provider)
        }
    }

    override fun teardownMockProvider() {
        mockProviders.forEach { provider ->
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                Log.e(TAG, "teardownMockProvider failed for provider=$provider", e)
                reportError(MockEngineError.Teardown(e), "teardownMockProvider failed for provider=$provider")
            }
        }
    }

    override fun setLocation(latitude: Double, longitude: Double) {
        mockProviders.forEach { provider ->
            try {
                ensureProviderEnabled(provider)
                val mockLocation = createMockLocation(provider, latitude, longitude)
                locationManager.setTestProviderLocation(provider, mockLocation)
                Log.d(TAG, "setLocation success provider=$provider lat=$latitude lng=$longitude")
            } catch (e: Exception) {
                val errorType = when (e) {
                    is SecurityException -> "SecurityException"
                    is IllegalArgumentException -> "IllegalArgumentException"
                    else -> e::class.java.simpleName
                }
                Log.e(TAG, "setLocation failed provider=$provider type=$errorType", e)
                reportError(MockEngineError.SetLocation(e), "setLocation failed provider=$provider type=$errorType")
                throw e
            }
        }
    }

    override fun getMockPermissionStatus(): MockPermissionStatus {
        return try {
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOpsManager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                appOpsManager.checkOpNoThrow(
                    AppOpsManager.OPSTR_MOCK_LOCATION,
                    Process.myUid(),
                    context.packageName
                )
            }
            if (mode == AppOpsManager.MODE_ALLOWED) {
                MockPermissionStatus.Allowed
            } else {
                MockPermissionStatus.NotAllowed
            }
        } catch (e: Exception) {
            reportError(MockEngineError.PermissionCheck(e), "isMockingAllowed check failed")
            MockPermissionStatus.CheckFailed(e)
        }
    }

    private fun ensureProviderEnabled(provider: String) {
        try {
            locationManager.addTestProvider(
                provider,
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
            Log.d(TAG, "addTestProvider success provider=$provider")
        } catch (e: IllegalArgumentException) {
            Log.d(TAG, "addTestProvider skipped provider=$provider already exists")
        } catch (e: SecurityException) {
            reportError(MockEngineError.Setup(e), "addTestProvider security exception provider=$provider")
            throw e
        }

        try {
            locationManager.setTestProviderEnabled(provider, true)
        } catch (e: Exception) {
            val errorType = when (e) {
                is SecurityException -> "SecurityException"
                is IllegalArgumentException -> "IllegalArgumentException"
                else -> e::class.java.simpleName
            }
            Log.e(TAG, "setTestProviderEnabled failed provider=$provider type=$errorType", e)
            reportError(MockEngineError.Setup(e), "setTestProviderEnabled failed provider=$provider type=$errorType")
            throw e
        }
    }

    private fun createMockLocation(provider: String, latitude: Double, longitude: Double): Location {
        return Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            altitude = 0.0
            accuracy = 5.0f
            time = System.currentTimeMillis()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            speed = 0.0f
            bearing = 0.0f
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
        private const val TAG = "MockGPS"
        private const val ERROR_BUFFER_CAPACITY = 16
    }
}
