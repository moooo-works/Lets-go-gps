package com.moooo_works.letsgogps.domain

import com.google.gson.Gson
import com.moooo_works.letsgogps.domain.repository.MockStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockSessionRestoreTest {

    private val gson = Gson()

    @Test
    fun `route session survives json round-trip`() {
        val session = PersistedMockSession(
            mode = MockSessionMode.ROUTE,
            savedAtMillis = 123L,
            bootTimeMillis = 45L,
            wasInjecting = true,
            waypoints = listOf(SerLatLng(25.0, 121.0), SerLatLng(25.1, 121.1)),
            speedMps = 5.0,
            loopMode = "BOUNCE",
            segmentIndex = 1,
            distanceCoveredInSegment = 42.0,
            isReturning = true,
        )
        assertEquals(session, parseMockSession(gson.toJson(session), gson))
    }

    @Test
    fun `single session survives json round-trip`() {
        val session = PersistedMockSession(
            mode = MockSessionMode.SINGLE,
            savedAtMillis = 1L,
            bootTimeMillis = 2L,
            wasInjecting = true,
            singleLat = 25.0,
            singleLng = 121.0,
        )
        assertEquals(session, parseMockSession(gson.toJson(session), gson))
    }

    @Test
    fun `null or blank json parses to null`() {
        assertNull(parseMockSession(null, gson))
        assertNull(parseMockSession("", gson))
        assertNull(parseMockSession("   ", gson))
    }

    @Test
    fun `corrupt json parses to null instead of throwing`() {
        assertNull(parseMockSession("{not valid json", gson))
    }

    private val now = 1_000_000_000L
    private val boot = 500_000_000L

    private fun route(
        savedAt: Long = now,
        bootTime: Long = boot,
        wasInjecting: Boolean = true,
    ) = PersistedMockSession(
        mode = MockSessionMode.ROUTE,
        savedAtMillis = savedAt,
        bootTimeMillis = bootTime,
        wasInjecting = wasInjecting,
        waypoints = listOf(SerLatLng(0.0, 0.0), SerLatLng(0.01, 0.0)),
        speedMps = 5.0,
    )

    // ─── Route completion vs stop (protects the recovery session) ─────────────

    @Test
    fun `idle while playing and not stopping is a real completion`() {
        assertTrue(isRouteCompletionOnIdle(isStopping = false, status = MockStatus.ROUTE_PLAYING))
        assertTrue(isRouteCompletionOnIdle(isStopping = false, status = MockStatus.ROUTE_PAUSED))
    }

    @Test
    fun `idle caused by stopping is NOT a completion so the session is preserved`() {
        // handleStop() drives the simulator to IDLE while mockStatus is briefly
        // still PLAYING; that must not be treated as a finished route (which would
        // clear the recovery session a killed process needs).
        assertFalse(isRouteCompletionOnIdle(isStopping = true, status = MockStatus.ROUTE_PLAYING))
        assertFalse(isRouteCompletionOnIdle(isStopping = true, status = MockStatus.ROUTE_PAUSED))
    }

    @Test
    fun `idle while not on a route is never a completion`() {
        assertFalse(isRouteCompletionOnIdle(isStopping = false, status = MockStatus.IDLE))
        assertFalse(isRouteCompletionOnIdle(isStopping = false, status = MockStatus.MOCKING))
    }

    @Test
    fun `null persisted is NO_OP`() {
        assertEquals(
            RestoreDecision.NO_OP,
            decideRestore(null, memoryHasActiveSession = false, currentBootTimeMillis = boot, nowMillis = now)
        )
    }

    @Test
    fun `active in-memory session is NO_OP so we never double-restore`() {
        assertEquals(
            RestoreDecision.NO_OP,
            decideRestore(route(), memoryHasActiveSession = true, currentBootTimeMillis = boot, nowMillis = now)
        )
    }

    @Test
    fun `same boot session within ttl resumes and resumes injection`() {
        assertEquals(
            RestoreDecision.RESTORE_AND_RESUME,
            decideRestore(route(), memoryHasActiveSession = false, currentBootTimeMillis = boot, nowMillis = now)
        )
    }

    @Test
    fun `boot time drift within tolerance still counts as same boot`() {
        assertEquals(
            RestoreDecision.RESTORE_AND_RESUME,
            decideRestore(route(bootTime = boot - 4_000), memoryHasActiveSession = false, currentBootTimeMillis = boot, nowMillis = now)
        )
    }

    @Test
    fun `different boot time means device rebooted so restore paused only`() {
        assertEquals(
            RestoreDecision.RESTORE_PAUSED,
            decideRestore(route(bootTime = boot - 60_000), memoryHasActiveSession = false, currentBootTimeMillis = boot, nowMillis = now)
        )
    }

    @Test
    fun `older than 24h is discarded`() {
        val stale = now - (25L * 60 * 60 * 1000)
        assertEquals(
            RestoreDecision.DISCARD,
            decideRestore(route(savedAt = stale), memoryHasActiveSession = false, currentBootTimeMillis = boot, nowMillis = now)
        )
    }

    @Test
    fun `paused-then-killed restores display only, not discarded`() {
        // wasInjecting=false means the user had paused. We still want the route
        // shown (paused) on return rather than silently dropping it.
        assertEquals(
            RestoreDecision.RESTORE_PAUSED,
            decideRestore(route(wasInjecting = false), memoryHasActiveSession = false, currentBootTimeMillis = boot, nowMillis = now)
        )
    }

    @Test
    fun `expired wins over paused — still discarded`() {
        val stale = now - (25L * 60 * 60 * 1000)
        assertEquals(
            RestoreDecision.DISCARD,
            decideRestore(route(savedAt = stale, wasInjecting = false), memoryHasActiveSession = false, currentBootTimeMillis = boot, nowMillis = now)
        )
    }
}
