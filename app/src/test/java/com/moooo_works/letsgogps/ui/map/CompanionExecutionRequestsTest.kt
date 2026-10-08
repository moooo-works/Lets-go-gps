package com.moooo_works.letsgogps.ui.map

import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.domain.repository.GeocodedLocation
import org.junit.Assert.*
import org.junit.Test

class CompanionExecutionRequestsTest {
    private val target = GeocodedLocation("selected", "description", LatLng(-12.0, -34.0))

    @Test fun `cancel invalidates late result even when active route is unchanged`() {
        val requests = CompanionExecutionRequests()
        val route = listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0))
        val pending = requests.create(CompanionAction.Locate(target), route)
        assertTrue(requests.isValid(pending, route))
        requests.invalidate()
        assertFalse(requests.isValid(pending, route))
    }

    @Test fun `new command prevents previous callback from executing`() {
        val requests = CompanionExecutionRequests()
        val old = requests.create(CompanionAction.Locate(target), null)
        val current = requests.create(CompanionAction.Explore(target.copy(latLng = LatLng(5.0, 6.0))), null)
        assertFalse(requests.isValid(old, null))
        assertTrue(requests.isValid(current, null))
        assertEquals(target, (old.action as CompanionAction.Locate).target)
    }

    @Test fun `route replacement while waiting requires another confirmation`() {
        val requests = CompanionExecutionRequests()
        val oldRoute = listOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0))
        val request = requests.create(CompanionAction.Explore(target), oldRoute)
        assertFalse(requests.isValid(request, null))
        assertFalse(requests.isValid(request, oldRoute.reversed()))
    }

    @Test fun `route payload and confirmation route cannot change through mutable input`() {
        val requests = CompanionExecutionRequests()
        val points = mutableListOf(LatLng(1.0, 2.0), LatLng(3.0, 4.0))
        val expected = points.toList()
        val request = requests.create(CompanionAction.UseRoute("saved", points), points)
        points.clear()
        assertEquals(expected, (request.action as CompanionAction.UseRoute).points)
        assertTrue(requests.isValid(request, expected))
    }
}
