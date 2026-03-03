package com.example.mockgps.service

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
import com.example.mockgps.MainActivity
import com.example.mockgps.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.mockgps.domain.LocationMockEngine
import com.example.mockgps.domain.RouteSimulator
import com.example.mockgps.domain.SimulationState
import com.example.mockgps.domain.repository.MockStateRepository
import com.example.mockgps.domain.repository.MockStatus
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject
import com.example.mockgps.data.engine.MockEngineError

@AndroidEntryPoint
class MockLocationService : Service() {

    @Inject
    lateinit var mockStateRepository: MockStateRepository

    @Inject
    lateinit var mockEngine: LocationMockEngine

    @Inject
    lateinit var routeSimulator: RouteSimulator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var locationPushJob: Job? = null
    private var isProviderSetup = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        serviceScope.launch {
            routeSimulator.simulationState.collect { state ->
                when (state) {
                    SimulationState.PLAYING -> {
                        mockStateRepository.setMockStatus(MockStatus.ROUTE_PLAYING)
                        updateNotification("Route Playing")
                    }
                    SimulationState.PAUSED -> {
                        mockStateRepository.setMockStatus(MockStatus.ROUTE_PAUSED)
                        updateNotification("Route Paused")
                    }
                    SimulationState.IDLE -> {
                        if (mockStateRepository.mockStatus.value == MockStatus.ROUTE_PLAYING || mockStateRepository.mockStatus.value == MockStatus.ROUTE_PAUSED) {
                            handleStop()
                        }
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY


        if (action == ACTION_START_SINGLE || action == ACTION_START_ROUTE) {
            try {
                startForeground(NOTIFICATION_ID, buildNotification("Starting..."))
            } catch (e: SecurityException) {
                Log.e(TAG, "Failed to start foreground service", e)
                mockStateRepository.setMockError(MockEngineError.Setup(e))
                mockStateRepository.setMockStatus(MockStatus.IDLE)
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
                Log.e(TAG, "Failed to setup mock provider", e)
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
            mockEngine.setLocation(lat, lng)
            val target = LatLng(lat, lng)
            mockStateRepository.setCurrentMockLocation(target)
            mockStateRepository.setMockStatus(MockStatus.MOCKING)
            updateNotification("Single Location Mocking")

            // Periodically push location to keep it alive if needed, but for single point we can just set it once
            // Actually, we need a continuous push to prevent OS from overriding or stopping mock.
            locationPushJob = serviceScope.launch {
                while(true) {
                    try {
                        mockEngine.setLocation(lat, lng)
                        kotlinx.coroutines.delay(1000)
                    } catch (e: Exception) {
                        mockStateRepository.setMockError(MockEngineError.SetLocation(e))
                        break
                    }
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
            routeSimulator.currentLocation.collect { location ->
                if (location != null) {
                    try {
                        mockEngine.setLocation(location.latitude, location.longitude)
                        mockStateRepository.setCurrentMockLocation(location)
                    } catch (e: Exception) {
                        mockStateRepository.setMockError(MockEngineError.SetLocation(e))
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
        stopLocationPushJob()
        routeSimulator.stop()

        if (isProviderSetup) {
            try {
                mockEngine.teardownMockProvider()
            } catch (e: Exception) {
                Log.e(TAG, "Teardown error", e)
            }
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

    override fun onDestroy() {
        handleStop()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Mock Location Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Runs the mock location engine in the background"
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MockLocationService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mock GPS Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (statusText == "Route Playing") {
            val pauseIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, MockLocationService::class.java).apply { action = ACTION_PAUSE_ROUTE },
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", pauseIntent)
        } else if (statusText == "Route Paused") {
            val resumeIntent = PendingIntent.getService(
                this,
                3,
                Intent(this, MockLocationService::class.java).apply { action = ACTION_RESUME_ROUTE },
                PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_media_play, "Resume", resumeIntent)
        }

        return builder.build()
    }

    private fun updateNotification(statusText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    companion object {
        private const val TAG = "MockLocationService"
        const val CHANNEL_ID = "MockLocationServiceChannel"
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
