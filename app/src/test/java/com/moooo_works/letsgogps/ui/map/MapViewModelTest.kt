package com.moooo_works.letsgogps.ui.map

import android.content.Context
import android.content.Intent
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.SimulationPoint
import com.moooo_works.letsgogps.domain.MockPermissionStatus
import com.moooo_works.letsgogps.domain.SimulationState
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckItem
import com.moooo_works.letsgogps.domain.healthcheck.HealthCheckState
import com.moooo_works.letsgogps.domain.healthcheck.ItemStatus
import com.moooo_works.letsgogps.domain.healthcheck.SystemHealthCheck
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.data.billing.RewardedAdManager
import com.moooo_works.letsgogps.domain.repository.ProRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import com.moooo_works.letsgogps.domain.repository.MockStatus
import com.moooo_works.letsgogps.domain.repository.TimezoneRepository
import com.moooo_works.letsgogps.service.MockLocationService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import com.moooo_works.letsgogps.data.model.Route
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.moooo_works.letsgogps.data.engine.MockEngineError
import com.moooo_works.letsgogps.domain.RouteProgress
import com.google.android.gms.maps.model.LatLng

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = android.app.Application::class)
class MapViewModelTest {

    private val mockEngine = mockk<LocationMockEngine>(relaxed = true)
    private val repository = mockk<LocationRepository>(relaxed = true)
    private val settingsRepository = mockk<SettingsRepository>(relaxed = true)
    private val mockStateRepository = mockk<MockStateRepository>(relaxed = true)
    private val routeSimulator = mockk<RouteSimulator>(relaxed = true)
    private val joystickOverlayManager = mockk<JoystickOverlayManager>(relaxed = true)
    private val proRepository = mockk<ProRepository>(relaxed = true)
    private val rewardedAdManager = mockk<RewardedAdManager>(relaxed = true)
    private val systemHealthCheck = mockk<SystemHealthCheck>(relaxed = true)
    private val timezoneRepository = mockk<TimezoneRepository>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val dispatcher = StandardTestDispatcher()

    private val simulationStateFlow = MutableStateFlow(SimulationState.IDLE)
    private val currentLocationFlow = MutableStateFlow<SimulationPoint?>(null)
    private val mockStatusFlow = MutableStateFlow(MockStatus.IDLE)
    private val currentMockLocationFlow = MutableStateFlow<LatLng?>(null)
    private val mockErrorFlow = MutableStateFlow<MockEngineError?>(null)
    private val lastCenterFlow = MutableStateFlow<LatLng?>(null)
    private val activeRouteWaypointsFlow = MutableStateFlow<List<LatLng>>(emptyList())
    private val isProActiveFlow = MutableStateFlow(false)
    private val routeProgressFlow = MutableStateFlow<RouteProgress?>(null)

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { repository.getAllLocations() } returns emptyFlow()
        every { repository.observeFolders() } returns emptyFlow()
        every { repository.observeRoutes() } returns emptyFlow()
        every { settingsRepository.observeStepQuotaUsedToday() } returns flowOf(0)
        every { settingsRepository.observeStepDailyQuota() } returns flowOf(10_000)
        every { settingsRepository.observeStepSyncEnabled() } returns flowOf(false)
        every { proRepository.isSubscriptionActive } returns MutableStateFlow(false)
        every { proRepository.featureCredits } returns MutableStateFlow(0)
        every { routeSimulator.simulationState } returns simulationStateFlow
        every { routeSimulator.currentLocation } returns currentLocationFlow
        every { mockStateRepository.mockStatus } returns mockStatusFlow
        every { mockStateRepository.currentMockLocation } returns currentMockLocationFlow
        every { mockStateRepository.mockError } returns mockErrorFlow
        every { settingsRepository.observeLastCenter() } returns lastCenterFlow
        every { settingsRepository.observeRouteSpeed() } returns flowOf(5.0)
        // Default: timezone check disabled to keep startMocking tests focused
        // on the mock pipeline rather than network behaviour. Override per-test
        // if the timezone path needs exercising.
        every { settingsRepository.observeEnableTimezoneCheck() } returns kotlinx.coroutines.flow.flowOf(false)

        every { mockStateRepository.activeRouteWaypoints } returns activeRouteWaypointsFlow
        every { proRepository.isProActive } returns isProActiveFlow
        every { proRepository.isAdFreeActive } returns MutableStateFlow(false)
        every { proRepository.adUnlockExpiryMillis } returns MutableStateFlow(0L)
        every { proRepository.subscriptionOffer } returns MutableStateFlow(null)
        coEvery { proRepository.grantAdUnlockHours(any()) } returns Unit
        every { routeSimulator.routeProgress } returns routeProgressFlow
        every { settingsRepository.observeMapType() } returns flowOf("NORMAL")
        every { settingsRepository.hasSeenOnboarding() } returns flowOf(true)
        every { settingsRepository.getLoopBounceTipSeenVersion() } returns flowOf(1)
        every { settingsRepository.hasSeenClipboardHintTip() } returns flowOf(true)
        every { settingsRepository.hasSeenGpxTip() } returns flowOf(true)
        every { settingsRepository.hasSeenJumpModeTip() } returns flowOf(true)

        // Default: every health-check item passes — individual tests override
        // when they want to exercise the blocking-failure path.
        every { systemHealthCheck.refresh() } returns HealthCheckState(
            HealthCheckItem.values().associateWith { ItemStatus.Passed }
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = MapViewModel(
        mockEngine, repository, mockStateRepository, settingsRepository,
        routeSimulator, joystickOverlayManager, proRepository, rewardedAdManager, systemHealthCheck,
        timezoneRepository, mockk(relaxed = true), context
    )

    @Test
    fun `companion denied Pro never dispatches or stops active simulation`() = runTest {
        val vm = createViewModel()
        advanceUntilIdle()
        vm.executeCompanionAction(CompanionAction.Locate(GeocodedLocation("selected", "", LatLng(-12.0, -34.0))))
        vm.awaitCompanionCommandForTest()
        verify(exactly = 0) { context.startForegroundService(any()) }
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `companion locate keeps original target when selection changes during startup`() = runTest {
        isProActiveFlow.value = true
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed
        every { settingsRepository.observeStepSyncEnabled() } returns flowOf(false)
        val vm = createViewModel()
        advanceUntilIdle()
        val target = LatLng(-12.0, -34.0)
        vm.executeCompanionAction(CompanionAction.Locate(GeocodedLocation("selected", "", target)))
        vm.selectSearchResult(GeocodedLocation("later", "", LatLng(40.0, 50.0)))
        vm.awaitCompanionCommandForTest()
        val captured = slot<Intent>()
        verify(exactly = 1) { context.startForegroundService(capture(captured)) }
        assertEquals(MockLocationService.ACTION_START_SINGLE, captured.captured.action)
        assertEquals(target.latitude, captured.captured.getDoubleExtra(MockLocationService.EXTRA_LAT, 0.0), 0.0)
        assertEquals(target.longitude, captured.captured.getDoubleExtra(MockLocationService.EXTRA_LNG, 0.0), 0.0)
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `companion AppOps denial keeps active route and sends no commands`() = runTest {
        isProActiveFlow.value = true
        mockStatusFlow.value = MockStatus.ROUTE_PAUSED
        activeRouteWaypointsFlow.value = listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0))
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.NotAllowed
        val vm = createViewModel()
        advanceUntilIdle()
        val route = vm.uiState.value.waypoints
        vm.executeCompanionAction(CompanionAction.Locate(GeocodedLocation("selected", "", LatLng(-12.0, -34.0))))
        vm.awaitCompanionCommandForTest()
        assertEquals(route, vm.uiState.value.waypoints)
        assertTrue(vm.uiState.value.mockError is MockError.NotMockAppSelected)
        verify(exactly = 0) { context.startForegroundService(any()) }
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `main explicit exploration replaces old companion credit target`() = runTest {
        isProActiveFlow.value = true
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed
        every { settingsRepository.observeStepSyncEnabled() } returns flowOf(true)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.executeCompanionAction(CompanionAction.Locate(GeocodedLocation("old companion", "", LatLng(-12.0, -34.0))))
        vm.awaitCompanionCommandForTest()
        assertTrue(vm.uiState.value.showStepSyncCreditDialog)
        val mainTarget = LatLng(40.0, 50.0)
        vm.selectSearchResult(GeocodedLocation("main", "", mainTarget))
        vm.startExplorationAtCenter()
        vm.startWithoutStepSync()
        val captured = slot<Intent>()
        verify(exactly = 1) { context.startForegroundService(capture(captured)) }
        assertEquals(MockLocationService.ACTION_START_EXPLORATION, captured.captured.action)
        assertEquals(mainTarget.latitude, captured.captured.getDoubleExtra(MockLocationService.EXTRA_LAT, 0.0), 0.0)
        assertEquals(mainTarget.longitude, captured.captured.getDoubleExtra(MockLocationService.EXTRA_LNG, 0.0), 0.0)
    }

    @Test
    fun `companion pending step cancellation cannot start without steps later`() = runTest {
        isProActiveFlow.value = true
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed
        every { settingsRepository.observeStepSyncEnabled() } returns flowOf(true)
        every { proRepository.isSubscriptionActive } returns MutableStateFlow(false)
        every { proRepository.featureCredits } returns MutableStateFlow(0)
        val vm = createViewModel()
        advanceUntilIdle()
        vm.executeCompanionAction(CompanionAction.Locate(GeocodedLocation("selected", "", LatLng(-12.0, -34.0))))
        vm.awaitCompanionCommandForTest()
        assertTrue(vm.uiState.value.showStepSyncCreditDialog)
        vm.dismissStepSyncCreditDialog()
        vm.startWithoutStepSync()
        advanceUntilIdle()
        verify(exactly = 0) { context.startForegroundService(any()) }
        verify(exactly = 0) { context.startService(any()) }
    }

    @Test
    fun `init loads last center from settings repository`() = runTest {
        val lastCenter = LatLng(25.1, 121.1)
        lastCenterFlow.value = lastCenter
        val viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(lastCenter, viewModel.uiState.value.centerLocation)
    }

    @Test
    fun `selectSearchResult updates center location`() = runTest {
        val viewModel = createViewModel()
        val selected = GeocodedLocation("Target", "Address", LatLng(10.0, 20.0))

        viewModel.selectSearchResult(selected)

        assertEquals(selected.latLng, viewModel.uiState.value.centerLocation)
    }

    @Test
    fun `startMocking succeeds and sends intent when permission granted`() = runTest {
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed
        val viewModel = createViewModel()

        val dispatched = kotlinx.coroutines.CompletableDeferred<Unit>()
        every { context.startForegroundService(any()) } answers {
            dispatched.complete(Unit)
            null
        }
        viewModel.startMocking()
        advanceUntilIdle()
        dispatched.await()

        val intentSlot = slot<Intent>()
        verify(atLeast = 1) { context.startForegroundService(capture(intentSlot)) }
        assertEquals(MockLocationService.ACTION_START_SINGLE, intentSlot.captured.action)
    }

    @Test
    fun `setSpeed rejects non positive speed`() = runTest {
        val viewModel = createViewModel()
        viewModel.setSpeed(0.0)
        verify(exactly = 0) { routeSimulator.setSpeed(0.0) }
        assertTrue(viewModel.uiState.value.mockError is MockError.InvalidInput)
    }

    @Test
    fun `init applies persisted route speed to simulator`() = runTest {
        every { settingsRepository.observeRouteSpeed() } returns flowOf(40.0)

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(40.0, viewModel.uiState.value.speedKmh, 0.0)
        verify { routeSimulator.setSpeed(40.0 / 3.6) }
    }

    @Test
    fun `route completed status keeps route mode idle and mocking`() = runTest {
        val viewModel = createViewModel()

        mockStatusFlow.value = MockStatus.ROUTE_COMPLETED
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isMocking)
        assertEquals(MapMode.ROUTE, viewModel.uiState.value.mapMode)
        assertEquals(SimulationState.IDLE, viewModel.uiState.value.simulationState)
    }
    @Test fun `VM stop prevents delayed controller route load from sending another command`() = runTest {
        isProActiveFlow.value=true
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed
        val reply=CompletableDeferred<RouteWithPoints?>()
        coEvery { repository.getRouteWithPoints(1) } coAnswers { withContext(NonCancellable) { reply.await() } }
        val vm=createViewModel()
        advanceUntilIdle()
        io.mockk.clearMocks(routeSimulator, answers = false)
        vm.floatingCompanionController.requestLoadRoute(1)
        runCurrent()
        vm.stopMocking()
        reply.complete(RouteWithPoints(Route(id=1,name="old"),listOf(
            RoutePoint(routeId=1,orderIndex=0,latitude=1.0,longitude=2.0),
            RoutePoint(routeId=1,orderIndex=1,latitude=3.0,longitude=4.0))))
        runCurrent()
        vm.awaitCompanionCommandForTest()
        verify(exactly=1) { context.startService(match { it.action==MockLocationService.ACTION_STOP }) }
        verify(exactly=0) { context.startForegroundService(any()) }
        verify(exactly=0) { routeSimulator.setRoute(any()) }
        io.mockk.coVerify(exactly=0) { proRepository.consumeFeatureCredits(any()) }
    }

    @Test fun `manager dismissal bridge clears credit pending and prevents late startup`() = runTest {
        isProActiveFlow.value=true
        every { mockEngine.getMockPermissionStatus() } returns MockPermissionStatus.Allowed
        every { settingsRepository.observeStepSyncEnabled() } returns flowOf(true)
        val dismissed=slot<(() -> Unit)>()
        every { joystickOverlayManager.setOnDismissedListener(capture(dismissed)) } just runs
        val vm=createViewModel()
        advanceUntilIdle()
        vm.executeCompanionAction(CompanionAction.Locate(GeocodedLocation("old","",LatLng(-12.0,-34.0))))
        vm.awaitCompanionCommandForTest()
        assertTrue(vm.uiState.value.showStepSyncCreditDialog)
        dismissed.captured.invoke()
        assertFalse(vm.uiState.value.showStepSyncCreditDialog)
        assertFalse(vm.uiState.value.isJoystickEnabled)
        vm.startWithoutStepSync()
        vm.awaitCompanionCommandForTest()
        verify(exactly=0) { context.startForegroundService(any()) }
        io.mockk.coVerify(exactly=0) { proRepository.consumeFeatureCredits(any()) }
    }

}
