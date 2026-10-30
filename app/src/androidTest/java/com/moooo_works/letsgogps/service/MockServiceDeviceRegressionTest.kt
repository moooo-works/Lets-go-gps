package com.moooo_works.letsgogps.service

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.view.InputDevice
import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.MotionEvent
import android.view.View
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.core.content.ContextCompat
import androidx.core.location.LocationCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.MainActivity
import com.moooo_works.letsgogps.MainApplication
import com.moooo_works.letsgogps.R
import com.moooo_works.letsgogps.data.engine.AndroidLocationMockEngine
import com.moooo_works.letsgogps.domain.*
import com.moooo_works.letsgogps.domain.repository.*
import com.moooo_works.letsgogps.ui.map.*
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/** Destructive fixtures are restricted to a disposable .recoverytest applicationId. */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MockServiceDeviceRegressionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private lateinit var state: MockStateRepository
    private lateinit var simulator: RouteSimulator
    private lateinit var settings: SettingsRepository
    private var activity: ActivityScenario<MainActivity>? = null
    private val origin = LatLng(25.0330, 121.5654)

    private fun field(instance: Any, name: String): Any =
        instance.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(instance)!!
    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private suspend fun until(label: String, timeout: Long = 10_000, condition: () -> Boolean) {
        withTimeoutOrNull(timeout) {
            while (!condition()) delay(50)
            true
        } ?: fail("Timed out: $label; status=${state.mockStatus.value}; error=${state.mockError.value?.javaClass?.simpleName}")
    }
    private fun serviceRunning(): Boolean =
        Regex("(?m)^\\s*\\* ServiceRecord\\{[^\\n]*MockLocationService").containsMatchIn(
            shell("dumpsys activity services ${context.packageName}")
        )
    private fun mockOverridesPresent(): Boolean {
        val dump = shell("dumpsys location")
        check(dump.startsWith("Location Manager State:")) { "Location dump must be valid, not an error response" }
        return Regex("""(?m)^\s+(?:gps|network) provider \[mock\]""").containsMatchIn(dump)
    }
    private fun send(action: String, extras: Intent.() -> Unit = {}) {
        val intent = Intent(context, MockLocationService::class.java).apply { this.action = action; extras() }
        main {
            if (action.startsWith("ACTION_START")) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }
    private fun single() = send(MockLocationService.ACTION_START_SINGLE) {
        putExtra(MockLocationService.EXTRA_LAT, origin.latitude)
        putExtra(MockLocationService.EXTRA_LNG, origin.longitude)
        putExtra(MockLocationService.EXTRA_STEP_SYNC_ALLOWED, false)
    }
    @android.annotation.SuppressLint("MissingPermission") // @Before verifies the isolated target's grant.
    private fun gps(): Location? =
        context.getSystemService(LocationManager::class.java).getLastKnownLocation(LocationManager.GPS_PROVIDER)
    private suspend fun active(expected: MockStatus = MockStatus.MOCKING) {
        until("actual service injection") {
            state.mockStatus.value == expected && state.currentMockLocation.value != null &&
                gps()?.let { LocationCompat.isMock(it) } == true
        }
        assertTrue(serviceRunning())
        assertTrue("The same provider parser must detect an active mock override", mockOverridesPresent())
        assertNull("Engine must not report an error", state.mockError.value)
    }
    private suspend fun stopped(requireCleanProviders: Boolean = true) {
        until("IDLE after cleanup") { state.mockStatus.value == MockStatus.IDLE }
        until("service destruction") { !serviceRunning() }
        assertNull(state.currentMockLocation.value)
        if (requireCleanProviders) assertFalse("Mock providers must be removed", mockOverridesPresent())
    }
    private suspend fun stop() { send(MockLocationService.ACTION_STOP); stopped() }
    private suspend fun fresh() {
        val location = freshDeviceLocation(com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context))
        assertNotNull("Real location must recover without opening Maps", location)
        assertFalse(LocationCompat.isMock(location!!))
    }
    private fun notificationStop() {
        val notifications = context.getSystemService(NotificationManager::class.java).activeNotifications
        val stop = notifications.asSequence().flatMap { it.notification.actions.orEmpty().asSequence() }
            .first { it.title.toString() == context.getString(R.string.action_stop) }
        stop.actionIntent.send()
    }

    @Before fun prepareIsolatedFixture() = runBlocking {
        assumeTrue("Only the disposable package may be modified", context.packageName.endsWith(".recoverytest"))
        assumeTrue(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        shell("cmd appops set ${context.packageName} android:mock_location allow")
        val restorer = EntryPointAccessors.fromApplication(context, MainApplication.RestorerEntryPoint::class.java).mockSessionRestorer()
        state = field(restorer, "mockStateRepository") as MockStateRepository
        simulator = field(restorer, "routeSimulator") as RouteSimulator
        settings = field(simulator, "settingsRepository") as SettingsRepository
        (field(restorer, "sessionRepository") as MockSessionRepository).clear()
        settings.setOnboardingDone()
        settings.setLoopBounceTipSeen(Int.MAX_VALUE)
        settings.setClipboardHintTipSeen()
        settings.setGpxTipSeen()
        settings.setJumpModeTipSeen()
        settings.setFloatingToolsTipSeen()
        settings.setEnableTimezoneCheck(false)
        settings.setStepSyncEnabled(false) // Never write the owner's Health Connect records.
        settings.setCoordinateJitter(false)
        settings.setRandomAltitude(false)
        simulator.stop()
        simulator.setLoopMode(LoopMode.NONE)
        simulator.setPlaybackMode(RoutePlaybackMode.WALK)
        simulator.setSpeed(20.0)
        activity = ActivityScenario.launch(MainActivity::class.java)
        send(MockLocationService.ACTION_STOP)
        stopped()
        state.clearError()
        state.setActiveRouteWaypoints(emptyList())
    }
    @After fun cleanupIsolatedFixture(): Unit = runBlocking {
        if (::state.isInitialized) {
            shell("cmd appops set ${context.packageName} android:mock_location allow")
            shell("input keyevent 224")
            if (!context.getSystemService(KeyguardManager::class.java).isDeviceSecure) shell("wm dismiss-keyguard")
            runCatching { send(MockLocationService.ACTION_STOP); stopped() }
            assertFalse("Test service must not leak", serviceRunning())
            assertFalse("Test mock providers must not leak", mockOverridesPresent())
        }
        activity?.close()
        Unit
    }

    @Test fun a_singleServiceStopAndRealFix() = runBlocking {
        single(); active(); stop(); fresh()
    }
    @Test fun b_tenStartStopCycles() = runBlocking {
        repeat(10) { single(); active(); stop(); state.clearError() }
        fresh()
    }
    @Test fun c_fiveRapidStopStartBurstsRemainRecoverable() = runBlocking {
        repeat(5) {
            single(); active()
            main {
                context.startService(Intent(context, MockLocationService::class.java).setAction(MockLocationService.ACTION_STOP))
                ContextCompat.startForegroundService(context, Intent(context, MockLocationService::class.java).apply {
                    action = MockLocationService.ACTION_START_SINGLE
                    putExtra(MockLocationService.EXTRA_LAT, origin.latitude)
                    putExtra(MockLocationService.EXTRA_LNG, origin.longitude)
                })
            }
            // Starts during STOPPING may be rejected; no crash or stuck service is allowed.
            stopped()
            state.clearError()
            single(); active(); stop()
        }
        fresh()
    }
    @Test fun d_routeWalkPauseResumeStop() = runBlocking {
        val points = listOf(origin, LatLng(origin.latitude + .005, origin.longitude))
        state.setActiveRouteWaypoints(points); simulator.setRoute(points)
        send(MockLocationService.ACTION_START_ROUTE); active(MockStatus.ROUTE_PLAYING)
        until("walking advances") { state.mockStatus.value == MockStatus.ROUTE_PLAYING && state.currentMockLocation.value?.let { it != points.first() } == true }
        send(MockLocationService.ACTION_PAUSE_ROUTE)
        until("paused") { state.mockStatus.value == MockStatus.ROUTE_PAUSED }
        val paused = checkNotNull(state.currentMockLocation.value) { "Paused route must retain its position" }
        delay(500); assertEquals(paused, state.currentMockLocation.value)
        send(MockLocationService.ACTION_RESUME_ROUTE)
        until("resumed walking advances") { state.mockStatus.value == MockStatus.ROUTE_PLAYING && state.currentMockLocation.value?.let { it != paused } == true }
        stop(); fresh()
    }
    @Test fun e_routeCompletionStillAllowsStop() = runBlocking {
        val points = listOf(origin, LatLng(origin.latitude + .000015, origin.longitude))
        state.setActiveRouteWaypoints(points); simulator.setRoute(points)
        send(MockLocationService.ACTION_START_ROUTE)
        until("route completes") { state.mockStatus.value == MockStatus.ROUTE_COMPLETED }
        assertNotNull(state.currentMockLocation.value)
        assertTrue(mockOverridesPresent())
        assertTrue(gps()?.let { LocationCompat.isMock(it) } == true)
        stop(); fresh()
    }
    @Test fun f_jumpRouteTransitionsAndStops() = runBlocking {
        val points = listOf(origin, LatLng(origin.latitude + .002, origin.longitude), LatLng(origin.latitude + .004, origin.longitude))
        state.setActiveRouteWaypoints(points); simulator.setRoute(points)
        simulator.setPlaybackMode(RoutePlaybackMode.JUMP); simulator.setJumpIntervalSec(1); simulator.setLoopMode(LoopMode.LOOP)
        send(MockLocationService.ACTION_START_ROUTE); active(MockStatus.ROUTE_PLAYING)
        val first = checkNotNull(state.currentMockLocation.value)
        until("jump advances to another waypoint") { state.mockStatus.value == MockStatus.ROUTE_PLAYING && state.currentMockLocation.value?.let { it != first } == true }
        stop(); fresh()
    }
    @Test fun g_explorationMovesAndStops() = runBlocking {
        send(MockLocationService.ACTION_START_EXPLORATION) {
            putExtra(MockLocationService.EXTRA_LAT, origin.latitude); putExtra(MockLocationService.EXTRA_LNG, origin.longitude)
            putExtra(MockLocationService.EXTRA_RADIUS_M, 20.0)
        }
        active(MockStatus.ROUTE_PLAYING)
        val first = checkNotNull(state.currentMockLocation.value)
        until("exploration moves") { state.mockStatus.value == MockStatus.ROUTE_PLAYING && state.currentMockLocation.value?.let { it != first } == true }
        stop(); fresh()
    }
    @Test fun h_teleportExplorationChangesAnchorAndStops() = runBlocking {
        val second = LatLng(origin.latitude + .002, origin.longitude)
        send(MockLocationService.ACTION_START_TELEPORT_EXPLORATION) {
            putExtra(MockLocationService.EXTRA_LATS, doubleArrayOf(origin.latitude, second.latitude))
            putExtra(MockLocationService.EXTRA_LNGS, doubleArrayOf(origin.longitude, second.longitude))
            putExtra(MockLocationService.EXTRA_DWELL_SEC, 1); putExtra(MockLocationService.EXTRA_COOLDOWN_SEC, 1)
            putExtra(MockLocationService.EXTRA_RADIUS_M, .1)
        }
        active(MockStatus.ROUTE_PLAYING)
        until("teleport reaches second anchor") { state.currentMockLocation.value?.let { it.latitude > origin.latitude + .001 } == true }
        stop(); fresh()
    }
    @Test fun i_realJoystickOverlayDragAndStop() = runBlocking {
        assumeTrue(android.provider.Settings.canDrawOverlays(context))
        single(); active()
        val ui = MutableStateFlow(MapUiState(isProActive = true, isMocking = true, centerLocation = origin))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        // Exercise first-show docking rather than relying on a saved edge position.
        context.getSharedPreferences("joystick_overlay_prefs", Context.MODE_PRIVATE).edit()
            .putInt("joystick_x", 100).putInt("joystick_y", 500).commit()
        val manager = JoystickOverlayManager(context)
        lateinit var controller: JoystickController
        var controllerCreated = false
        try {
            main {
                controller = JoystickController(ui, scope, manager, state, context,
                    onStopMocking = { context.startService(Intent(context, MockLocationService::class.java).setAction(MockLocationService.ACTION_STOP)) },
                    onCameraMove = { point -> ui.update { it.copy(centerLocation = point) } }, onSetTransportMode = {})
                controllerCreated = true
                controller.toggle()
            }
            until("real overlay laid out") { (field(manager, "composeView") as View).width > 0 }
            val view = field(manager, "composeView") as View
            val at = IntArray(2)
            // Initial layout snaps the window to an edge. Wait for rendering/input
            // geometry, then use the actual screen location (not raw LayoutParams).
            val ready = CountDownLatch(1)
            main { view.postOnAnimation { view.postOnAnimation { ready.countDown() } } }
            assertTrue(ready.await(2, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
            val rendered = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull("Overlay frame must be rendered before injecting a drag", rendered)
            rendered?.recycle()
            val automationInfo = instrumentation.uiAutomation.serviceInfo
            automationInfo.flags = automationInfo.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            instrumentation.uiAutomation.serviceInfo = automationInfo
            var windowBounds: Rect? = null
            until("actual overlay window bounds are available") {
                windowBounds = instrumentation.uiAutomation.windows.firstNotNullOfOrNull { window ->
                    val bounds = Rect().also(window::getBoundsInScreen)
                    bounds.takeIf { window.root?.packageName == context.packageName && it.width() == view.width && it.height() == view.height }
                }
                windowBounds != null
            }
            at[0] = windowBounds!!.left; at[1] = windowBounds!!.top
            val density = context.resources.displayMetrics.density
            val x = at[0] + 76 * density; val y = at[1] + 130 * density
            val down = SystemClock.uptimeMillis()
            fun pointer(action: Int, px: Float, py: Float) {
                MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, px, py, 0).also {
                    it.source = InputDevice.SOURCE_TOUCHSCREEN
                    instrumentation.sendPointerSync(it); it.recycle()
                }
            }
            pointer(MotionEvent.ACTION_DOWN, x, y)
            repeat(6) { delay(40); pointer(MotionEvent.ACTION_MOVE, x + (it + 1) * 10, y) }
            var direction = 0f
            main { direction = field(controller, "currentX") as Float }
            assertTrue("Native drag must reach the joystick handle", direction > 0f)
            until("joystick gesture changes injected position") { state.currentMockLocation.value?.longitude?.let { it > origin.longitude } == true }
            pointer(MotionEvent.ACTION_UP, x + 60, y)
            val stopX = at[0] + view.width / 2f; val stopY = at[1] + view.height - 32 * density
            pointer(MotionEvent.ACTION_DOWN, stopX, stopY); pointer(MotionEvent.ACTION_UP, stopX, stopY)
            stopped()
            assertFalse(ui.value.isJoystickEnabled)
            fresh()
        } finally {
            main { if (controllerCreated) controller.onCleared(); manager.hide() }
            scope.cancel()
        }
    }
    @Test fun j_backgroundInjectionAndNotificationStop() = runBlocking {
        single(); active()
        val before = gps()!!.elapsedRealtimeNanos
        shell("input keyevent 3")
        delay(3_000)
        assertTrue("Background FGS must keep injecting", (gps()?.elapsedRealtimeNanos ?: 0) > before)
        notificationStop(); stopped()
        shell("am start -n ${context.packageName}/${MainActivity::class.java.name}")
        fresh()
    }
    @Test fun k_mockAuthorityRevokedStopAndRecovery() = runBlocking {
        single(); active()
        shell("cmd appops set ${context.packageName} android:mock_location deny")
        send(MockLocationService.ACTION_STOP); stopped(requireCleanProviders = false)
        assertNotNull("Revoked authority must preserve a visible failure", state.mockError.value)
        shell("cmd appops set ${context.packageName} android:mock_location allow")
        send(MockLocationService.ACTION_STOP); stopped(); state.clearError()
        single(); active(); stop(); fresh()
    }
    @Test fun l_screenOffInjectionAndNotificationStop() = runBlocking {
        val keyguard = context.getSystemService(KeyguardManager::class.java)
        val manualUnlock = InstrumentationRegistry.getArguments().getString("manualUnlock") == "true"
        assumeTrue("Secure screen-off case requires an available human; no lock bypass", !keyguard.isDeviceSecure || manualUnlock)
        single(); active()
        val before = gps()!!.elapsedRealtimeNanos
        try {
            shell("input keyevent 223"); delay(3_000)
            assertTrue("Screen-off FGS must keep injecting", (gps()?.elapsedRealtimeNanos ?: 0) > before)
            notificationStop(); stopped()
        } finally {
            shell("input keyevent 224")
            if (!keyguard.isDeviceSecure) shell("wm dismiss-keyguard")
        }
        if (keyguard.isDeviceSecure) {
            val waiting = java.io.File(context.filesDir, "screen-off-waiting")
            waiting.writeText("injection and notification stop passed; awaiting manual unlock")
            try { until("manual keyguard unlock", 120_000) { !keyguard.isKeyguardLocked } }
            finally { waiting.delete() }
            instrumentation.waitForIdleSync()
        }
        fresh()
    }
}
