package com.moooo_works.letsgogps.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.moooo_works.letsgogps.MainActivity
import com.moooo_works.letsgogps.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject
import com.moooo_works.letsgogps.data.engine.MockEngineError

@AndroidEntryPoint
class MockLocationService : Service() {

    @Inject
    lateinit var mockStateRepository: MockStateRepository

    @Inject
    lateinit var mockEngine: LocationMockEngine

    @Inject
    lateinit var routeSimulator: RouteSimulator

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var locationPushJob: Job? = null
    private var isProviderSetup = false
    private var currentSpeedKmh: Double = 5.0
    private var consecutiveInjectionFailures = 0

    // Settings Cache to avoid frequent DataStore I/O during rapid injection
    private var cachedAltitude: Double = 15.0
    private var cachedRandomAltitude: Boolean = false
    private var cachedJitter: Boolean = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // 1. Maintain settings cache
        serviceScope.launch {
            settingsRepository.observeAltitude().collect { cachedAltitude = it }
        }
        serviceScope.launch {
            settingsRepository.observeRandomAltitude().collect { cachedRandomAltitude = it }
        }
        serviceScope.launch {
            settingsRepository.observeCoordinateJitter().collect { cachedJitter = it }
        }

        // 2. Observe Route Simulation state
        serviceScope.launch {
            routeSimulator.simulationState.collect { state ->
                when (state) {
                    SimulationState.PLAYING -> {
                        mockStateRepository.setMockStatus(MockStatus.ROUTE_PLAYING)
                        updateNotification(MockStatus.ROUTE_PLAYING)
                    }
                    SimulationState.PAUSED -> {
                        mockStateRepository.setMockStatus(MockStatus.ROUTE_PAUSED)
                        updateNotification(MockStatus.ROUTE_PAUSED)
                    }
                    SimulationState.IDLE -> {
                        if (mockStateRepository.mockStatus.value == MockStatus.ROUTE_PLAYING || 
                            mockStateRepository.mockStatus.value == MockStatus.ROUTE_PAUSED) {
                            handleStop()
                        }
                    }
                }
            }
        }

        // 3. REACTIVE INJECTION CORE: Use combine to ensure immediate response to location or status changes
        serviceScope.launch {
            combine(
                mockStateRepository.currentMockLocation,
                mockStateRepository.mockStatus
            ) { location, status -> location to status }
                .collect { (location, status) ->
                    if (location != null && status == MockStatus.MOCKING) {
                        performInjection(location)
                    }
                }
        }

        // 4. Track speed for notifications
        serviceScope.launch {
            settingsRepository.observeRouteSpeed().collect { speed ->
                currentSpeedKmh = speed
                if (mockStateRepository.mockStatus.value == MockStatus.ROUTE_PLAYING) {
                    updateNotification(MockStatus.ROUTE_PLAYING)
                }
            }
        }
    }

    private fun performInjection(location: LatLng) {
        try {
            val altitude = if (cachedRandomAltitude) {
                cachedAltitude + kotlin.random.Random.nextDouble(-0.5, 0.5)
            } else cachedAltitude

            val target = if (cachedJitter) {
                val latOffset = kotlin.random.Random.nextDouble(-0.00003, 0.00003)
                val lngOffset = kotlin.random.Random.nextDouble(-0.00003, 0.00003)
                LatLng(location.latitude + latOffset, location.longitude + lngOffset)
            } else location

            mockEngine.setLocation(target.latitude, target.longitude, altitude = altitude)
            consecutiveInjectionFailures = 0
        } catch (e: Exception) {
            Log.e(TAG, "Location injection failed", e)
            consecutiveInjectionFailures++

            // SecurityException 表示模擬位置權限已被撤銷，立即停止
            val isPermissionRevoked = e is SecurityException || e.cause is SecurityException
            if (isPermissionRevoked || consecutiveInjectionFailures >= MAX_INJECTION_FAILURES) {
                Log.w(TAG, "Stopping service: permission revoked=$isPermissionRevoked failures=$consecutiveInjectionFailures")
                mockStateRepository.setMockError(MockEngineError.SetLocation(e))
                handleStop()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        if (action == ACTION_START_SINGLE || action == ACTION_START_ROUTE) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification(MockStatus.IDLE))
            } catch (e: SecurityException) {
                mockStateRepository.setMockError(MockEngineError.Setup(e))
                stopSelf()
                return START_NOT_STICKY
            }
        }

        when (action) {
            ACTION_START_SINGLE -> handleStartSingle(intent)
            ACTION_START_ROUTE -> handleStartRoute()
            ACTION_PAUSE_ROUTE -> handlePauseRoute()
            ACTION_RESUME_ROUTE -> handleResumeRoute()
            ACTION_STOP -> handleStop()
        }

        return START_NOT_STICKY
    }

    private fun ensureProviderSetup() {
        if (!isProviderSetup) {
            try {
                mockEngine.setupMockProvider()
                isProviderSetup = true
            } catch (e: Exception) {
                mockStateRepository.setMockError(MockEngineError.Setup(e))
                handleStop()
            }
        }
    }

    private fun handleStartSingle(intent: Intent) {
        val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)

        routeSimulator.stop()
        stopLocationPushJob()
        ensureProviderSetup()
        if (!isProviderSetup) return

        try {
            val target = LatLng(lat, lng)
            // Important: Set location first then status to trigger reactive injection immediately
            mockStateRepository.setCurrentMockLocation(target)
            mockStateRepository.setMockStatus(MockStatus.MOCKING)
            updateNotification(MockStatus.MOCKING)

            // Keep-alive loop: ensure the system receives updates at least once per second
            // even if the user isn't moving the joystick
            locationPushJob = serviceScope.launch {
                while (true) {
                    val current = mockStateRepository.currentMockLocation.value
                    if (current != null && mockStateRepository.mockStatus.value == MockStatus.MOCKING) {
                        performInjection(current)
                    }
                    delay(200)
                }
            }
        } catch (e: Exception) {
            mockStateRepository.setMockError(MockEngineError.SetLocation(e))
            handleStop()
        }
    }

    private fun handleStartRoute() {
        ensureProviderSetup()
        if (!isProviderSetup) return
        stopLocationPushJob()

        locationPushJob = serviceScope.launch {
            routeSimulator.currentLocation.collect { point ->
                if (point != null) {
                    try {
                        mockEngine.setLocation(
                            point.latLng.latitude,
                            point.latLng.longitude,
                            point.bearing,
                            point.speed,
                            point.altitude
                        )
                        mockStateRepository.setCurrentMockLocation(point.latLng)
                    } catch (e: Exception) {
                        mockStateRepository.setMockError(MockEngineError.SetLocation(e))
                        handleStop()
                    }
                }
            }
        }
        routeSimulator.play(serviceScope)
    }

    private fun handlePauseRoute() {
        routeSimulator.pause()
    }

    private fun handleResumeRoute() {
        if (routeSimulator.simulationState.value == SimulationState.PAUSED) {
            routeSimulator.play(serviceScope)
        }
    }

    private fun handleStop() {
        consecutiveInjectionFailures = 0
        stopLocationPushJob()
        routeSimulator.stop()
        if (isProviderSetup) {
            try { mockEngine.teardownMockProvider() } catch (e: Exception) {}
            isProviderSetup = false
        }
        mockStateRepository.setMockStatus(MockStatus.IDLE)
        mockStateRepository.setCurrentMockLocation(null)
        stopForeground(true)
        stopSelf()
    }

    private fun stopLocationPushJob() {
        locationPushJob?.cancel()
        locationPushJob = null
    }

    private fun updateNotification(status: MockStatus) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: MockStatus): Notification {
        val pendingIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE)

        val stopIntent = PendingIntent.getService(this, 1,
            Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE)

        val contentText = when (status) {
            MockStatus.ROUTE_PLAYING -> getString(R.string.status_route_playing, "%.0f".format(currentSpeedKmh))
            MockStatus.ROUTE_PAUSED  -> getString(R.string.status_route_paused)
            MockStatus.MOCKING       -> getString(R.string.status_mocking)
            MockStatus.IDLE          -> getString(R.string.status_idle)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.status_service_running))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_mockgps)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_delete, getString(R.string.action_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Mock Service", NotificationManager.IMPORTANCE_DEFAULT)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        handleStop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MockLocationService"
        private const val MAX_INJECTION_FAILURES = 5  // 連續失敗 5 次（約 5 秒）後停止
        const val CHANNEL_ID = "MockLocationServiceChannelV3"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_SINGLE = "ACTION_START_SINGLE"
        const val ACTION_START_ROUTE = "ACTION_START_ROUTE"
        const val ACTION_PAUSE_ROUTE = "ACTION_PAUSE_ROUTE"
        const val ACTION_RESUME_ROUTE = "ACTION_RESUME_ROUTE"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
    }
}
