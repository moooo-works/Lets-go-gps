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
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
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
import com.moooo_works.letsgogps.domain.RoutePlaybackMode
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SerLatLng
import com.moooo_works.letsgogps.domain.SimulationPoint
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.repository.MockSessionRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.domain.health.StepAccumulator
import com.moooo_works.letsgogps.domain.health.StepSyncEngine
import com.google.android.gms.maps.model.LatLng
import java.time.Instant
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

    @Inject
    lateinit var stepSyncEngine: StepSyncEngine

    /** 累積模擬移動距離，換算成可寫入的步數批次。只由 serviceScope 存取。 */
    @VisibleForTesting
    internal val stepAccumulator = StepAccumulator()

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
    private var cachedStepSyncEnabled: Boolean = false
    private var cachedStepLengthMeters: Double = StepAccumulator.DEFAULT_STEP_LENGTH_METERS
    private var cachedStepDailyQuota: Int = 0

    /** 當日配額耗盡的通知只發一次，避免每個同步週期都轟炸使用者。 */
    private var quotaExhaustedNotified = false

    /** handleStop 可能被呼叫兩次，保證收尾 flush 只跑一次。 */
    private var stepsFlushed = false

    /** 上次成功寫入步數的 elapsedRealtime，用來算時間窗的上限。 */
    private var lastStepSyncElapsedMs = 0L

    /** 過速提醒只發一次，成功寫入後重置。 */
    private var tooFastNotified = false

    /**
     * 本次 session 是否獲准寫入步數，由啟動 intent 的 extra 決定。
     *
     * 與設定開關分離：設定表達「我想用」，這個表達「這次付過了」。
     * 免費使用者次數不足時選擇「這次不用步數同步」，設定會維持開啟，
     * 但這個旗標是 false。
     */
    private var sessionStepSyncAllowed = false

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
        serviceScope.launch {
            settingsRepository.observeStepSyncEnabled().collect { cachedStepSyncEnabled = it }
        }
        serviceScope.launch {
            settingsRepository.observeStepLengthMeters().collect { cachedStepLengthMeters = it }
        }
        serviceScope.launch {
            settingsRepository.observeStepDailyQuota().collect { cachedStepDailyQuota = it }
        }

        // 1b. 步數同步迴圈。間隔刻意隨機，固定週期本身就是可辨識的特徵。
        serviceScope.launch {
            while (isActive) {
                delay(nextStepSyncDelayMillis())
                runCatching { syncAccumulatedSteps() }
                    .onFailure { Log.w(TAG, "步數同步失敗", it) }
            }
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

            // 用「加抖動前」的 location 累積距離：抖動幅度約 ±3 公尺，
            // 若用抖動後的座標，站著不動也會持續累積假距離。
            accumulateStepDistance(location)
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

    // ── 步數同步 ──────────────────────────────────────────────────────────

    /**
     * 累積一段模擬移動。[location] 必須是**加抖動前**的原始座標。
     *
     * 瞬移的排除交給 [StepAccumulator] 的單段距離門檻——這裡不能用 speed 判斷，
     * 搖桿移動走的是 speed 為 0 的注入路徑，但那是真實的連續移動，該計入。
     */
    private fun accumulateStepDistance(location: LatLng) {
        if (!isStepSyncActive()) return

        // 速度用**設定值**判斷，不量測。
        // 量測版本（單段距離 ÷ 實際耗時）實測會誤判：記時間的位置在
        // setLocation() 這個 IPC 之後，延遲浮動數十毫秒，而 19 km/h 距門檻
        // 只有 5% 餘裕，雜訊直接吃掉整個餘裕。設定值是精確的，也與 UI 警示同源。
        if (currentSpeedKmh > StepAccumulator.MAX_STEP_SPEED_KMH) {
            notifyTooFastOnce()
            return
        }

        // 跳點模式不計步：waypoint 間距若小於瞬移門檻會被當成走路，
        // 但那本來就是瞬移。設定說明也是這樣寫的。
        if (routeSimulator.currentPlaybackMode() == RoutePlaybackMode.JUMP) return

        stepAccumulator.addDistance(location)
    }

    /** 設定開著（想用）**且**本次 session 獲准（付過了）才會寫步數。 */
    private fun isStepSyncActive(): Boolean = cachedStepSyncEnabled && sessionStepSyncAllowed

    /** 下次同步的間隔：10–25 秒隨機。 */
    private fun nextStepSyncDelayMillis(): Long =
        STEP_SYNC_BASE_DELAY_MS + kotlin.random.Random.nextLong(STEP_SYNC_JITTER_MS)

    /**
     * 把累積的距離換算成步數寫入 Health Connect。
     *
     * 任何一批寫入失敗就停止本輪——失敗多半代表權限被撤銷或 Health Connect
     * 不可用，繼續嘗試沒有意義，未寫入的距離留到下一輪。
     */
    private suspend fun syncAccumulatedSteps() {
        if (!isStepSyncActive()) return

        val usedToday = settingsRepository.observeStepQuotaUsedToday().first()
        val quotaRemaining = cachedStepDailyQuota - usedToday
        if (quotaRemaining <= 0) {
            if (cachedStepDailyQuota > 0) notifyQuotaExhaustedOnce()
            return
        }
        quotaExhaustedNotified = false

        // 距上次成功寫入實際經過的秒數。時間窗以此為上限，才不會讓批次
        // 往回追溯到比實際經過時間更早的位置而與前一輪重疊。
        val nowElapsed = SystemClock.elapsedRealtime()
        val spanSeconds = ((nowElapsed - lastStepSyncElapsedMs) / 1000L)
            .coerceAtLeast(1L)

        val batches = stepAccumulator.drainBatches(
            stepLength = cachedStepLengthMeters,
            quotaRemaining = quotaRemaining,
            now = Instant.now(),
            spanSeconds = spanSeconds,
        )
        if (batches.isEmpty()) return

        var written = 0L
        for (batch in batches) {
            if (!stepSyncEngine.write(batch.steps, batch.start, batch.end)) break
            written += batch.steps
        }

        if (written > 0L) {
            stepAccumulator.commit(written, cachedStepLengthMeters)
            settingsRepository.addStepQuotaUsed(written.toInt())
            lastStepSyncElapsedMs = nowElapsed
            tooFastNotified = false
        }
    }

    /**
     * 平均速度超過步行／跑步範圍時提醒一次。
     *
     * 只發一次：高速模擬會每個同步週期都觸發，每次都發通知會變轟炸。
     * 速度降回範圍內並成功寫入後旗標重置，下次超速會再提醒。
     */
    private fun notifyTooFastOnce() {
        if (tooFastNotified) return
        tooFastNotified = true
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.step_too_fast_title))
                .setContentText(getString(R.string.step_too_fast_body))
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.step_too_fast_body))
                )
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID_STEP_TOO_FAST, notification)
        } catch (e: Exception) {
            Log.w(TAG, "過速通知發送失敗", e)
        }
    }

    private fun notifyQuotaExhaustedOnce() {
        if (quotaExhaustedNotified) return
        quotaExhaustedNotified = true
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.step_quota_exhausted_title))
                .setContentText(getString(R.string.step_quota_exhausted_body))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIFICATION_ID_QUOTA_EXHAUSTED, notification)
        } catch (e: Exception) {
            Log.w(TAG, "配額耗盡通知發送失敗", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: return START_NOT_STICKY

        if (action == ACTION_START_SINGLE || action == ACTION_START_ROUTE ||
            action == ACTION_START_EXPLORATION || action == ACTION_START_TELEPORT_EXPLORATION
        ) {
            isStopping = false // starting fresh — IDLE-as-completion detection re-armed
            // 計費決策在 UI 層做完了，服務只接收結果。缺 extra 一律視為不允許。
            sessionStepSyncAllowed = intent.getBooleanExtra(EXTRA_STEP_SYNC_ALLOWED, false)
            stepsFlushed = false
            tooFastNotified = false
            lastStepSyncElapsedMs = SystemClock.elapsedRealtime()
            stepAccumulator.reset()
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
                    playbackMode = routeSimulator.currentPlaybackMode().name,
                    jumpIntervalSec = routeSimulator.currentJumpIntervalSec(),
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
        flushStepsOnStop()
        stopForeground(true)
        stopSelf()
    }

    /**
     * 模擬停止時把剩餘步數寫完。
     *
     * 用 [terminalScope] 而非 serviceScope——後者在 onDestroy 立刻被取消，
     * 寫入會來不及完成。handleStop 可能被呼叫兩次（ACTION_STOP 再 onDestroy），
     * 故以 [stepsFlushed] 保證只跑一次。
     */
    private fun flushStepsOnStop() {
        if (stepsFlushed) return
        stepsFlushed = true
        if (!isStepSyncActive()) return
        if (stepAccumulator.pendingDistanceMeters <= 0.0) return

        terminalScope.launch {
            runCatching { syncAccumulatedSteps() }
                .onFailure { Log.w(TAG, "停止時的步數 flush 失敗", it) }
        }
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

        /** 步數同步間隔下限（毫秒）。 */
        private const val STEP_SYNC_BASE_DELAY_MS = 10_000L

        /** 疊加在下限之上的隨機量（毫秒），實際間隔落在 10–25 秒。 */
        private const val STEP_SYNC_JITTER_MS = 15_000L

        /**
         * 服務終止後仍需完成的短暫寫入（步數收尾 flush）。
         * 刻意獨立於 serviceScope——後者在 onDestroy 立刻被取消。
         */
        private val terminalScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val CHANNEL_ID = "MockLocationServiceChannelV5"
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_ID_QUOTA_EXHAUSTED = 2
        const val NOTIFICATION_ID_STEP_TOO_FAST = 3
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

        /**
         * 本次 session 是否允許寫入步數。由 UI 層的計次閘門決定後傳進來——
         * 服務不參與計費判斷，只執行決定。
         */
        const val EXTRA_STEP_SYNC_ALLOWED = "EXTRA_STEP_SYNC_ALLOWED"
        const val EXTRA_DWELL_SEC = "EXTRA_DWELL_SEC"
        const val EXTRA_COOLDOWN_SEC = "EXTRA_COOLDOWN_SEC"
    }
}
