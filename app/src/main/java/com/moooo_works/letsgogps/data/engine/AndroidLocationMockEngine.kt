package com.moooo_works.letsgogps.data.engine

import android.app.AppOpsManager
import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.MockPermissionStatus
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

class AndroidLocationMockEngine : LocationMockEngine {

    private val context: Context
    private val sdkInt: Int
    private val locationManager: LocationManager
    private val appOpsManager: AppOpsManager

    @Inject
    constructor(@ApplicationContext context: Context) : this(context, Build.VERSION.SDK_INT)

    constructor(@ApplicationContext context: Context, sdkInt: Int) {
        this.context = context
        this.sdkInt = sdkInt
        this.locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        this.appOpsManager = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        this.fusedClient = LocationServices.getFusedLocationProviderClient(context)
    }
    private lateinit var fusedClient: com.google.android.gms.location.FusedLocationProviderClient

    private val mockProviders = listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        "passive"
    )
    private val enabledProviders = linkedSetOf<String>()
    private val setupFailures = linkedMapOf<String, String>()

    private val _errors = MutableSharedFlow<MockEngineError>(extraBufferCapacity = ERROR_BUFFER_CAPACITY)
    val errors: SharedFlow<MockEngineError> = _errors.asSharedFlow()

    override fun setupMockProvider() {
        enabledProviders.clear()
        setupFailures.clear()

        mockProviders.forEach { provider ->
            tryEnableProvider(provider)
        }

        if (enabledProviders.isEmpty()) {
            val details = setupFailures.values.joinToString(separator = " | ")
            val message = "No test providers enabled${if (details.isNotBlank()) ". $details" else ""}"
            val error = IllegalStateException(message)
            reportError(MockEngineError.Setup(error), message)
            throw error
        }

        // Enable FLP mock mode to directly override Fused Location Provider
        try {
            fusedClient.setMockMode(true)
            Log.d(TAG, "FusedLocationProviderClient mock mode enabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to enable FLP mock mode: ${e.message}")
        }
    }

    override fun teardownMockProvider() {
        enabledProviders.forEach { provider ->
            try {
                locationManager.removeTestProvider(provider)
            } catch (e: Exception) {
                val message = "Provider $provider teardown failed: ${e::class.java.simpleName}: ${e.message}"
                Log.e(TAG, message, e)
                reportError(MockEngineError.Teardown(e), message)
            }
        }
        enabledProviders.clear()
        setupFailures.clear()

        try {
            fusedClient.setMockMode(false)
            Log.d(TAG, "FusedLocationProviderClient mock mode disabled")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable FLP mock mode: ${e.message}")
        }
    }

    override fun setLocation(
        latitude: Double,
        longitude: Double,
        bearing: Float,
        speed: Float,
        altitude: Double
    ) {
        if (enabledProviders.isEmpty()) {
            val details = setupFailures.values.joinToString(separator = " | ")
            val message = "No test providers enabled${if (details.isNotBlank()) ". $details" else ""}"
            val error = IllegalStateException(message)
            reportError(MockEngineError.Setup(error), message)
            throw error
        }

        // CRITICAL: Synchronize timestamps for all providers to avoid flickering
        val nowMs = System.currentTimeMillis()
        val nowNanos = if (sdkInt >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            SystemClock.elapsedRealtimeNanos()
        } else 0L

        enabledProviders.forEach { provider ->
            try {
                val mockLocation = createMockLocation(
                    provider, latitude, longitude, bearing, speed, altitude, nowMs, nowNanos
                )
                locationManager.setTestProviderLocation(provider, mockLocation)
            } catch (e: Exception) {
                val message = "Provider $provider setLocation failed: ${e::class.java.simpleName}: ${e.message}"
                Log.e(TAG, message, e)
                reportError(MockEngineError.SetLocation(e), message)
            }
        }

        // Inject directly into FusedLocationProviderClient to override Play Services FLP
        try {
            val flpLocation = createMockLocation(
                "fused", latitude, longitude, bearing, speed, altitude, nowMs, nowNanos
            )
            fusedClient.setMockLocation(flpLocation)
        } catch (e: Exception) {
            Log.w(TAG, "FLP setMockLocation failed: ${e.message}")
        }
    }

    override fun getMockPermissionStatus(): MockPermissionStatus {
        return try {
            val mode = if (sdkInt >= Build.VERSION_CODES.Q) {
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
            if (mode == AppOpsManager.MODE_ALLOWED) MockPermissionStatus.Allowed else MockPermissionStatus.NotAllowed
        } catch (e: Exception) {
            reportError(MockEngineError.PermissionCheck(e), "isMockingAllowed check failed")
            MockPermissionStatus.CheckFailed(e)
        }
    }

    private fun tryEnableProvider(provider: String) {
        runCatching {
            if (sdkInt >= Build.VERSION_CODES.S) {
                locationManager.addTestProvider(provider, createProviderProperties())
            } else {
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
            }
            Log.d(TAG, "addTestProvider success provider=$provider")
        }.onFailure { addError ->
            if (addError !is IllegalArgumentException) {
                val message = "Provider $provider setup failed: ${addError::class.java.simpleName}: ${addError.message}"
                setupFailures[provider] = message
                Log.e(TAG, message, addError)
                reportError(MockEngineError.Setup(addError), message)
            }
        }

        runCatching {
            locationManager.setTestProviderEnabled(provider, true)
            enabledProviders.add(provider)
            setupFailures.remove(provider)
            Log.d(TAG, "setTestProviderEnabled success provider=$provider")
        }.onFailure { enableError ->
            val message = "Provider $provider setup failed: ${enableError::class.java.simpleName}: ${enableError.message}"
            setupFailures[provider] = message
            Log.e(TAG, message, enableError)
            reportError(MockEngineError.Setup(enableError), message)
        }
    }

    private fun createProviderProperties(): ProviderProperties {
        return ProviderProperties.Builder()
            .setHasNetworkRequirement(false)
            .setHasSatelliteRequirement(false)
            .setHasCellRequirement(false)
            .setHasMonetaryCost(false)
            .setHasAltitudeSupport(true)
            .setHasSpeedSupport(true)
            .setHasBearingSupport(true)
            .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
            .setAccuracy(ProviderProperties.ACCURACY_FINE)
            .build()
    }

    private fun createMockLocation(
        provider: String,
        latitude: Double,
        longitude: Double,
        bearing: Float,
        speed: Float,
        altitude: Double,
        timeMs: Long,
        elapsedNanos: Long
    ): Location {
        return Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
            this.altitude = altitude
            this.accuracy = 2.0f 
            this.time = timeMs
            if (sdkInt >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                this.elapsedRealtimeNanos = elapsedNanos
            }
            if (sdkInt >= Build.VERSION_CODES.Q) {
                this.elapsedRealtimeUncertaintyNanos = 0.0
            }
            this.speed = speed
            this.bearing = bearing
            if (sdkInt >= Build.VERSION_CODES.O) {
                this.verticalAccuracyMeters = 2.0f
                this.speedAccuracyMetersPerSecond = 0.5f
                this.bearingAccuracyDegrees = 1.0f
            }
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
        private const val TAG = "MockGPS/MockEngine"
        private const val ERROR_BUFFER_CAPACITY = 16
    }
}
