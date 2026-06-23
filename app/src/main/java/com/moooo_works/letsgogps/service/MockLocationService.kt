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
import com.moooo_works.letsgogps.domain.isRouteCompletionOnIdle
import com.moooo_works.letsgogps.domain.MockSessionMode
import com.moooo_works.letsgogps.domain.PersistedMockSession
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SerLatLng
import com.moooo_works.letsgogps.domain.SimulationPoint
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.repository.MockSessionRepository
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

    @Inject
    lateinit var mockSessionRepository: MockSessionRepository

    // The session we are currently persisting for crash-recovery, kept in memory
    // so progress ticks only have to rewrite the changed fields. Null = nothing
    // to recover. Cleared only on explicit stop / route completion, NOT on
    // onDestroy — a killed process must leave its session on disk to restore.
    @Volatile
    private var activeSession: PersistedMockSession? = null

    // True while handleStop() is tearing down. Stop drives the simulator to IDLE
    // before mockStatus flips to IDLE; without this flag the IDLE collector would
    // misread that transient as "route completed" and clear the recovery session.
    @Volatile
    private var isStopping = false

    // Default dispatcher (not Main) so the route simulator's tick loop and
    // location-injection collectors keep running at full cadence when the user
    // switches MockGPS to background. Android 11+ throttles a backgrounded
    // app's Main thread under Doze / App Standby, which would otherwise stall
    // delay() in the simulator and "freeze" the mock location for consumers
    // like Pokemon GO / Pikmin Bloom. None of the work in this scope touches
    // UI: startForeground/stopForeground are called from onStartCommand /
    // handleStop which run on the system's binder thread, not in serviceScope.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var locationPushJob: Job? = null
    private var isProviderSetup = false
    private var currentSpeedKmh: Double = 5.0
    private var consecutiveInjectionFailures = 0
    private var currentRouteProgress: com.moooo_works.letsgogps.domain.RouteProgress? = null

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
                        if (isRouteCompletionOnIdle(isStopping, mockStateRepository.mockStatus.value)) {
                            handleRouteCompleted()
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
                routeSimulator.setSpeed(speed / KMH_TO_MPS_DIVISOR)
                if (mockStateRepository.mockStatus.value == MockStatus.ROUTE_PLAYING) {
                    updateNotification(MockStatus.ROUTE_PLAYING)
                }
            }
        }

        // 5. Track route progress for notifications
        @OptIn(FlowPreview::class)
        serviceScope.launch {
            routeSimulator.routeProgress.sample(1000).collect { progress ->
                currentRouteProgress = progress
                val status = mockStateRepository.mockStatus.value
                if (status == MockStatus.ROUTE_PLAYING || status == MockStatus.ROUTE_PAUSED) {
                    updateNotification(status)
                }
            }
        }

        // 6. Persist route progress so a killed process can resume mid-route.
        // ponytail: 3s throttle — a kill loses at most ~3s of progress, which is fine.
        @OptIn(FlowPreview::class)
        serviceScope.launch {
            routeSimulator.routeProgress.sample(3000).collect {
                val base = activeSession ?: return@collect
                if (base.mode == MockSessionMode.ROUTE &&
                    routeSimulator.simulationState.value == SimulationState.PLAYING
                ) {
                    val snap = routeSimulator.snapshotProgress()
                    persistSession(
                        base.copy(
                            savedAtMillis = System.currentTimeMillis(),
                            wasInjecting = true,
                            segmentIndex = snap.segmentIndex,
                            distanceCoveredInSegment = snap.distanceCoveredInSegment,
                            isReturning = snap.isReturning,
                        )
                    )
                }
            }
        }
    }

    private fun bootTimeMillis(): Long =
        System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()

    /** Save + remember the session. Suspend DataStore write is fire-and-forget. */
    private fun persistSession(session: PersistedMockSession) {
        activeSession = session
        serviceScope.launch { mockSessionRepository.save(session) }
    }

    /** Clear the recovery session — only on explicit stop / completion. */
    private fun clearPersistedSession() {
        activeSession = null
        serviceScope.launch { mockSessionRepository.clear() }
    }

    private fun performInjection(location: LatLng, bearing: Float = 0f, speed: Float = 0f, applyJitter: Boolean = true) {
        try {
            val altitude = if (cachedRandomAltitude) {
                cachedAltitude + kotlin.random.Random.nextDouble(-0.5, 0.5)
            } else cachedAltitude

            val target = if (applyJitter && cachedJitter) {
                val latOffset = kotlin.random.Random.nextDouble(-0.00003, 0.00003)
                val lngOffset = kotlin.random.Random.nextDouble(-0.00003, 0.00003)
                LatLng(location.latitude + latOffset, location.longitude + lngOffset)
            } else location

            mockEngine.setLocation(
                target.latitude,
                target.longitude,
                bearing = bearing,
                speed = speed,
                altitude = altitude
            )
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

        if (action == ACTION_START_SINGLE || action == ACTION_START_ROUTE ||
            action == ACTION_START_EXPLORATION || action == ACTION_START_TELEPORT_EXPLORATION
        ) {
            isStopping = false // starting fresh — IDLE-as-completion detection re-armed
            try {
                startForeground(NOTIFICATION_ID, buildNotification(MockStatus.IDLE))
            } catch (e: RuntimeException) {
                // Android 12+ throws ForegroundServiceStartNotAllowedException (an
                // IllegalStateException, NOT SecurityException) when a FGS starts
                // outside an allowed window; SecurityException covers a missing FGS
                // type permission. Catch both so a failed start degrades instead of
                // crashing, and clear any recovery session so we never crash-loop on
                // every launch. See task fix-fgs-restore-crash.
                clearPersistedSession()
                mockStateRepository.setMockError(MockEngineError.Setup(e))
                stopSelf()
                return START_NOT_STICKY
            }
        }

        when (action) {
            ACTION_START_SINGLE -> handleStartSingle(intent)
            ACTION_START_ROUTE -> handleStartRoute()
            ACTION_START_EXPLORATION -> handleStartExploration(intent)
            ACTION_START_TELEPORT_EXPLORATION -> handleStartTeleportExploration(intent)
            ACTION_PAUSE_ROUTE -> handlePauseRoute()
            ACTION_RESUME_ROUTE -> handleResumeRoute()
            ACTION_STOP -> {
                clearPersistedSession() // explicit user stop — discard recovery
                handleStop()
            }
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

            persistSession(
                PersistedMockSession(
                    mode = MockSessionMode.SINGLE,
                    savedAtMillis = System.currentTimeMillis(),
                    bootTimeMillis = bootTimeMillis(),
                    wasInjecting = true,
                    singleLat = lat,
                    singleLng = lng,
                )
            )

            // Keep-alive loop: ensure the system receives updates at least once per 500ms
            // even if the user isn't moving the joystick. 500ms (2Hz) is enough.
            locationPushJob = serviceScope.launch {
                while (true) {
                    val current = mockStateRepository.currentMockLocation.value
                    if (current != null && mockStateRepository.mockStatus.value == MockStatus.MOCKING) {
                        performInjection(current)
                    }
                    delay(500)
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
            // 1. COLLECT & INJECT: When simulator emits, we inject immediately
            launch {
                routeSimulator.currentLocation.collect { point ->
                    if (point != null) {
                        mockStateRepository.setCurrentMockLocation(point.latLng)
                        performInjection(point.latLng, point.bearing, point.speed, applyJitter = false)
                    }
                }
            }

            // 2. KEEP-ALIVE: If the simulator is idle/paused, re-inject last point every 1s
            // to prevent system from reclaiming the location, but don't do it rapidly.
            while (isActive) {
                if (routeSimulator.simulationState.value != SimulationState.PLAYING) {
                    val current = mockStateRepository.currentMockLocation.value
                    if (current != null) {
                        performInjection(current)
                    }
                }
                delay(1000) // Lower frequency for keep-alive to avoid interference
            }
        }

        // Persist the route session for crash-recovery. waypoints come from the
        // repository (already populated by the UI before playRoute); progress is
        // whatever the simulator currently holds (0 on a fresh play, mid-route on
        // a restore-driven resume). The 3s progress ticker keeps it fresh.
        val waypoints = mockStateRepository.activeRouteWaypoints.value
        if (waypoints.size >= 2) {
            val snap = routeSimulator.snapshotProgress()
            persistSession(
                PersistedMockSession(
                    mode = MockSessionMode.ROUTE,
                    savedAtMillis = System.currentTimeMillis(),
                    bootTimeMillis = bootTimeMillis(),
                    wasInjecting = true,
                    waypoints = waypoints.map { SerLatLng(it.latitude, it.longitude) },
                    speedMps = routeSimulator.currentSpeedMps(),
                    loopMode = routeSimulator.currentLoopMode().name,
                    segmentIndex = snap.segmentIndex,
                    distanceCoveredInSegment = snap.distanceCoveredInSegment,
                    isReturning = snap.isReturning,
                )
            )
        }

        routeSimulator.play(serviceScope)
    }

    /**
     * Spiral around a single anchor point. Reuses the route-mode injection
     * pipeline (collect simulator.currentLocation → push to provider) so the
     * service-side wiring is identical to a normal route.
     */
    private fun handleStartExploration(intent: Intent) {
        ensureProviderSetup()
        if (!isProviderSetup) return
        stopLocationPushJob()

        val lat = intent.getDoubleExtra(EXTRA_LAT, 0.0)
        val lng = intent.getDoubleExtra(EXTRA_LNG, 0.0)
        val radius = intent.getDoubleExtra(EXTRA_RADIUS_M, RouteSimulator.DEFAULT_EXPLORATION_RADIUS_M)
        val center = LatLng(lat, lng)

        startSimulatorCollector()
        routeSimulator.playExploration(serviceScope, center, radius)
    }

    /**
     * Hop between [targets], spiral-explore each. Targets arrive as parallel
     * DoubleArrays (lats[i], lngs[i]) since List<LatLng> isn't trivially
     * Parcelable through Intent extras.
     */
    private fun handleStartTeleportExploration(intent: Intent) {
        ensureProviderSetup()
        if (!isProviderSetup) return
        stopLocationPushJob()

        val lats = intent.getDoubleArrayExtra(EXTRA_LATS) ?: return
        val lngs = intent.getDoubleArrayExtra(EXTRA_LNGS) ?: return
        if (lats.size != lngs.size || lats.isEmpty()) return
        val targets = lats.indices.map { LatLng(lats[it], lngs[it]) }
        val dwell = intent.getIntExtra(EXTRA_DWELL_SEC, RouteSimulator.DEFAULT_TELEPORT_DWELL_SEC)
        val cooldown = intent.getIntExtra(EXTRA_COOLDOWN_SEC, RouteSimulator.DEFAULT_TELEPORT_COOLDOWN_SEC)
        val radius = intent.getDoubleExtra(EXTRA_RADIUS_M, RouteSimulator.DEFAULT_EXPLORATION_RADIUS_M)

        startSimulatorCollector()
        routeSimulator.playTeleportExploration(serviceScope, targets, dwell, cooldown, radius)
    }

    /**
     * Shared collector + keep-alive used by route, exploration, and teleport
     * exploration modes. Mirrors handleStartRoute's pipeline.
     */
    private fun startSimulatorCollector() {
        locationPushJob = serviceScope.launch {
            launch {
                routeSimulator.currentLocation.collect { point ->
                    if (point != null) {
                        mockStateRepository.setCurrentMockLocation(point.latLng)
                        performInjection(point.latLng, point.bearing, point.speed, applyJitter = false)
                    }
                }
            }
            while (isActive) {
                if (routeSimulator.simulationState.value != SimulationState.PLAYING) {
                    val current = mockStateRepository.currentMockLocation.value
                    if (current != null) performInjection(current)
                }
                delay(1000)
            }
        }
    }

    private fun handlePauseRoute() {
        routeSimulator.pause()
        // Paused-then-killed must NOT auto-inject on return — only restore display.
        activeSession?.let {
            persistSession(it.copy(wasInjecting = false, savedAtMillis = System.currentTimeMillis()))
        }
    }

    private fun handleResumeRoute() {
        if (routeSimulator.simulationState.value == SimulationState.PAUSED) {
            routeSimulator.play(serviceScope)
            activeSession?.let {
                persistSession(it.copy(wasInjecting = true, savedAtMillis = System.currentTimeMillis()))
            }
        }
    }

    private fun handleRouteCompleted() {
        currentRouteProgress = null
        clearPersistedSession() // route finished — nothing to recover
        val finalLocation = mockStateRepository.currentMockLocation.value
        if (finalLocation == null) {
            handleStop()
            return
        }
        mockStateRepository.setMockStatus(MockStatus.ROUTE_COMPLETED)
        updateNotification(MockStatus.ROUTE_COMPLETED)
        performInjection(finalLocation, speed = 0f, applyJitter = false)
    }

    private fun handleStop() {
        isStopping = true
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
        val pauseIntent = PendingIntent.getService(this, 2,
            Intent(this, MockLocationService::class.java).apply { action = ACTION_PAUSE_ROUTE },
            PendingIntent.FLAG_IMMUTABLE)
        val resumeIntent = PendingIntent.getService(this, 3,
            Intent(this, MockLocationService::class.java).apply { action = ACTION_RESUME_ROUTE },
            PendingIntent.FLAG_IMMUTABLE)

        val contentText = when (status) {
            MockStatus.ROUTE_PLAYING -> {
                val base = getString(R.string.status_route_playing, "%.0f".format(currentSpeedKmh))
                val p = currentRouteProgress
                if (p != null) "$base (${String.format(java.util.Locale.getDefault(), "%.1f / %.1f km", p.coveredKm, p.totalKm)})" else base
            }
            MockStatus.ROUTE_PAUSED  -> {
                val base = getString(R.string.status_route_paused)
                val p = currentRouteProgress
                if (p != null) "$base (${String.format(java.util.Locale.getDefault(), "%.1f / %.1f km", p.coveredKm, p.totalKm)})" else base
            }
            MockStatus.MOCKING, MockStatus.ROUTE_COMPLETED -> getString(R.string.status_mocking)
            MockStatus.IDLE          -> getString(R.string.status_idle)
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.status_service_running))
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_stat_mockgps)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)

        // Status-aware action buttons:
        //   - PLAYING: pause + stop (mid-route, user might want to walk away)
        //   - PAUSED:  resume + stop
        //   - MOCKING (single point): stop only — pause makes no sense
        //   - IDLE:    stop only (rare; shown briefly during setup)
        // Order matters — first action gets the "compact" priority on lock screen.
        when (status) {
            MockStatus.ROUTE_PLAYING -> {
                builder.addAction(android.R.drawable.ic_media_pause, getString(R.string.map_route_pause), pauseIntent)
                builder.addAction(android.R.drawable.ic_delete, getString(R.string.action_stop), stopIntent)
            }
            MockStatus.ROUTE_PAUSED -> {
                builder.addAction(android.R.drawable.ic_media_play, getString(R.string.action_resume), resumeIntent)
                builder.addAction(android.R.drawable.ic_delete, getString(R.string.action_stop), stopIntent)
            }
            MockStatus.MOCKING, MockStatus.ROUTE_COMPLETED, MockStatus.IDLE -> {
                builder.addAction(android.R.drawable.ic_delete, getString(R.string.action_stop), stopIntent)
            }
        }

        val p = currentRouteProgress
        if ((status == MockStatus.ROUTE_PLAYING || status == MockStatus.ROUTE_PAUSED) && p != null) {
            builder.setProgress(100, (p.fraction * 100).toInt(), false)
        } else {
            builder.setProgress(0, 0, false)
        }

        return builder.build()
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
        private const val KMH_TO_MPS_DIVISOR = 3.6
        const val CHANNEL_ID = "MockLocationServiceChannelV5"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_SINGLE = "ACTION_START_SINGLE"
        const val ACTION_START_ROUTE = "ACTION_START_ROUTE"
        const val ACTION_START_EXPLORATION = "ACTION_START_EXPLORATION"
        const val ACTION_START_TELEPORT_EXPLORATION = "ACTION_START_TELEPORT_EXPLORATION"
        const val ACTION_PAUSE_ROUTE = "ACTION_PAUSE_ROUTE"
        const val ACTION_RESUME_ROUTE = "ACTION_RESUME_ROUTE"
        const val ACTION_STOP = "ACTION_STOP"
        const val EXTRA_LAT = "EXTRA_LAT"
        const val EXTRA_LNG = "EXTRA_LNG"
        const val EXTRA_RADIUS_M = "EXTRA_RADIUS_M"
        const val EXTRA_LATS = "EXTRA_LATS"
        const val EXTRA_LNGS = "EXTRA_LNGS"
        const val EXTRA_DWELL_SEC = "EXTRA_DWELL_SEC"
        const val EXTRA_COOLDOWN_SEC = "EXTRA_COOLDOWN_SEC"
    }
}
