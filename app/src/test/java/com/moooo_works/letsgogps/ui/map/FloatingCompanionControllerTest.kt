package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.data.model.SavedLocation
import com.moooo_works.letsgogps.data.model.Route
import com.moooo_works.letsgogps.data.model.RoutePoint
import com.moooo_works.letsgogps.data.model.RouteWithPoints
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.SearchRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FloatingCompanionControllerTest {
    private val repository = mockk<LocationRepository>(relaxed = true)
    private val search = mockk<SearchRepository>(relaxed = true)
    private val settings = mockk<SettingsRepository>(relaxed = true)
    private val map = MutableStateFlow(MapUiState(isProActive = true))
    private val actions = mutableListOf<CompanionAction>()
    private val target = GeocodedLocation("目標", "說明", LatLng(25.1, 121.5))

    @Test fun `coordinate search selects target without starting simulation`() = runTest {
        val controller = FloatingCompanionController(map, repository, search, settings, backgroundScope, actions::add)
        controller.setQuery("-34.6037, -58.3816")
        controller.search()
        runCurrent()
        assertNotNull(controller.state.value.selectedLocation)
        assertEquals(-34.6037, controller.state.value.selectedLocation!!.latLng.latitude, 0.000001)
        assertTrue(actions.isEmpty())
        assertNull(map.value.currentMockLocation)
    }

    @Test fun `selection on an active route requires confirmation and cancellation preserves route`() = runTest {
        val points = listOf(LatLng(1.0, 2.0), LatLng(2.0, 3.0))
        map.value = map.value.copy(isMocking = true, mapMode = MapMode.ROUTE, waypoints = points)
        val controller = FloatingCompanionController(map, repository, search, settings, backgroundScope, actions::add)
        controller.select(target)
        controller.requestLocate()
        assertTrue(actions.isEmpty())
        assertNotNull(controller.state.value.replacement)
        controller.cancelReplacement()
        assertNull(controller.state.value.replacement)
        assertEquals(points, map.value.waypoints)
        assertTrue(map.value.isMocking)
    }

    @Test fun `general place search uses the existing repository`() = runTest {
        coEvery { search.search("公園") } returns Result.success(listOf(target))
        val controller = FloatingCompanionController(map, repository, search, settings, backgroundScope, actions::add)
        controller.setQuery("公園")
        controller.search()
        runCurrent()
        assertEquals(listOf(target), controller.state.value.searchResults)
        assertTrue(actions.isEmpty())
    }

    @Test fun `current coordinate never falls back to map center or inactive stale location`() {
        val center = LatLng(20.0, 30.0)
        assertNull(currentCompanionCoordinate(MapUiState(centerLocation = center)))
        assertNull(currentCompanionCoordinate(MapUiState(currentMockLocation = center, isMocking = false)))
        assertEquals(center, currentCompanionCoordinate(MapUiState(currentMockLocation = center, isMocking = true)))
        assertEquals("-34.603700, -58.381600", formatCompanionCoordinate(LatLng(-34.6037, -58.3816)))
    }
    @Test fun `late search cannot overwrite a newer coordinate selection`() = runTest {
        val reply = CompletableDeferred<Result<List<GeocodedLocation>>>()
        coEvery { search.search("old") } coAnswers { withContext(NonCancellable) { reply.await() } }
        val controller = FloatingCompanionController(map, repository, search, settings, backgroundScope, actions::add)
        controller.setQuery("old")
        controller.search()
        runCurrent()
        controller.setQuery("25.0,121.0")
        controller.search()
        reply.complete(Result.success(listOf(target)))
        runCurrent()
        assertTrue(controller.state.value.searchResults.isEmpty())
        assertEquals(LatLng(25.0,121.0), controller.state.value.selectedLocation!!.latLng)
        assertTrue(actions.isEmpty())
    }

    @Test fun `changed active route invalidates replacement confirmation`() = runTest {
        map.value = map.value.copy(isMocking=true,mapMode=MapMode.ROUTE,waypoints=listOf(LatLng(1.0,2.0),LatLng(3.0,4.0)))
        val controller = FloatingCompanionController(map, repository, search, settings, backgroundScope, actions::add)
        controller.select(target)
        controller.requestLocate()
        map.value = map.value.copy(waypoints=listOf(LatLng(5.0,6.0),LatLng(7.0,8.0)))
        controller.confirmReplacement()
        assertTrue(actions.isEmpty())
        assertEquals(CompanionMessage.ROUTE_CHANGED, controller.state.value.message)
    }

    @Test fun `close discards late search results`() = runTest {
        val reply = CompletableDeferred<Result<List<GeocodedLocation>>>()
        coEvery { search.search("old") } coAnswers { withContext(NonCancellable) { reply.await() } }
        val controller = FloatingCompanionController(map, repository, search, settings, backgroundScope, actions::add)
        controller.setQuery("old")
        controller.search()
        runCurrent()
        controller.close()
        reply.complete(Result.success(listOf(target)))
        runCurrent()
        assertFalse(controller.state.value.searching)
        assertTrue(controller.state.value.searchResults.isEmpty())
    }

    @Test fun `expired pro cannot execute an earlier replacement`() = runTest {
        map.value = map.value.copy(isMocking=true,mapMode=MapMode.ROUTE)
        val controller = FloatingCompanionController(map, repository, search, settings, backgroundScope, actions::add)
        controller.select(target)
        controller.requestLocate()
        map.value=map.value.copy(isProActive=false)
        controller.confirmReplacement()
        assertTrue(actions.isEmpty())
        assertEquals(CompanionMessage.NEED_PRO,controller.state.value.message)
    }

    @Test fun `saved search intersects query folder and favorite`() = runTest {
        map.value=map.value.copy(savedLocations=listOf(
            SavedLocation(1,"Park one",1.0,2.0,true,folderId=7),
            SavedLocation(2,"Park two",3.0,4.0,false,folderId=7),
            SavedLocation(3,"Park three",5.0,6.0,true,folderId=8),
            SavedLocation(4,"Museum",7.0,8.0,true,folderId=7)))
        val controller=FloatingCompanionController(map,repository,search,settings,backgroundScope,actions::add)
        controller.setQuery("park")
        controller.setFolder(7)
        controller.setFavoritesOnly(true)
        assertEquals(listOf(1),controller.state.value.savedLocations.map { it.id })
        assertTrue(actions.isEmpty())
    }

    @Test fun `older saved route cannot supersede a newer locate command`() = runTest {
        val reply=CompletableDeferred<RouteWithPoints?>()
        coEvery { repository.getRouteWithPoints(1) } coAnswers { withContext(NonCancellable) { reply.await() } }
        val controller=FloatingCompanionController(map,repository,search,settings,backgroundScope,actions::add)
        controller.requestLoadRoute(1)
        runCurrent()
        controller.select(target)
        controller.requestLocate()
        reply.complete(RouteWithPoints(Route(id=1,name="old"),listOf(
            RoutePoint(routeId=1,orderIndex=0,latitude=1.0,longitude=2.0),
            RoutePoint(routeId=1,orderIndex=1,latitude=3.0,longitude=4.0))))
        runCurrent()
        assertEquals(listOf(CompanionAction.Locate(target)),actions)
    }

    @Test fun `explicit stop invalidates a pending saved route even if read ignores cancellation`() = runTest {
        val reply=CompletableDeferred<RouteWithPoints?>()
        coEvery { repository.getRouteWithPoints(1) } coAnswers { withContext(NonCancellable) { reply.await() } }
        val controller=FloatingCompanionController(map,repository,search,settings,backgroundScope,actions::add)
        controller.requestLoadRoute(1)
        runCurrent()
        controller.cancelPendingActions()
        reply.complete(RouteWithPoints(Route(id=1,name="old"),listOf(
            RoutePoint(routeId=1,orderIndex=0,latitude=1.0,longitude=2.0),
            RoutePoint(routeId=1,orderIndex=1,latitude=3.0,longitude=4.0))))
        runCurrent()
        assertTrue(actions.isEmpty())
    }

}
