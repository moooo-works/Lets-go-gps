package com.moooo_works.letsgogps.service

import android.os.SystemClock
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.maps.model.LatLng
import com.moooo_works.letsgogps.domain.LoopMode
import com.moooo_works.letsgogps.domain.MockSessionMode
import com.moooo_works.letsgogps.domain.RestoreDecision
import com.moooo_works.letsgogps.domain.RouteProgressSnapshot
import com.moooo_works.letsgogps.domain.RouteSimulator
import com.moooo_works.letsgogps.domain.decideRestore
import com.moooo_works.letsgogps.domain.repository.MockSessionRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.MockStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restores a persisted mock session when the app returns to the foreground after
 * its process was killed (e.g. aggressive OEM background management). Registered
 * once from [com.moooo_works.letsgogps.MainApplication]; the first ON_START after
 * a cold start drives recovery. See [decideRestore] for the policy.
 *
 * Restore only ever re-populates the *display* state (route shown as paused, single
 * point shown). It never starts the foreground service: starting a FGS from this
 * background lifecycle callback throws ForegroundServiceStartNotAllowedException on
 * Android 12+ (and is especially strict on some OEMs), which previously crash-looped
 * the app on every launch. The user resumes by pressing play from a real foreground
 * interaction, continuing from the restored progress.
 */
@Singleton
class MockSessionRestorer @Inject constructor(
    private val sessionRepository: MockSessionRepository,
    private val mockStateRepository: MockStateRepository,
    private val routeSimulator: RouteSimulator,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                scope.launch { maybeRestore() }
            }
        })
    }

    private suspend fun maybeRestore() {
        // Defense in depth: restore runs in a background coroutine right after a
        // cold start. Any uncaught throw here reaches the default handler and
        // crashes the app ON LAUNCH — and since the session stays on disk, it
        // crash-loops every reopen ("app killed → can't open it again"). So we
        // never let restore brick startup: on ANY failure, drop the session and
        // give up quietly. The user just won't see the route restored.
        try {
            val memoryHasActiveSession =
                mockStateRepository.mockStatus.value != MockStatus.IDLE ||
                    mockStateRepository.activeRouteWaypoints.value.isNotEmpty()

            val persisted = sessionRepository.load()
            val decision = decideRestore(
                persisted = persisted,
                memoryHasActiveSession = memoryHasActiveSession,
                currentBootTimeMillis = System.currentTimeMillis() - SystemClock.elapsedRealtime(),
                nowMillis = System.currentTimeMillis(),
            )

            when (decision) {
                RestoreDecision.NO_OP -> Unit
                RestoreDecision.DISCARD -> sessionRepository.clear()
                // Both RESUME and PAUSED restore display only — never auto-start the
                // service. Resuming injection is a foreground user action (press play).
                RestoreDecision.RESTORE_AND_RESUME,
                RestoreDecision.RESTORE_PAUSED -> persisted?.let { restoreDisplay(it) }
            }
        } catch (e: Exception) {
            runCatching { sessionRepository.clear() }
        }
    }

    private fun restoreDisplay(session: com.moooo_works.letsgogps.domain.PersistedMockSession) {
        when (session.mode) {
            MockSessionMode.ROUTE -> {
                val waypoints = session.waypoints.map { LatLng(it.lat, it.lng) }
                if (waypoints.size < 2) return
                mockStateRepository.setActiveRouteWaypoints(waypoints)
                routeSimulator.setRoute(waypoints)
                routeSimulator.setSpeed(session.speedMps)
                routeSimulator.setLoopMode(runCatching { LoopMode.valueOf(session.loopMode) }.getOrDefault(LoopMode.NONE))
                routeSimulator.restoreProgress(
                    RouteProgressSnapshot(session.segmentIndex, session.distanceCoveredInSegment, session.isReturning)
                )
                // Show route + progress, paused; the user presses play to resume
                // from here (the normal foreground start path drives the service).
                mockStateRepository.setMockStatus(MockStatus.ROUTE_PAUSED)
            }
            MockSessionMode.SINGLE -> {
                val lat = session.singleLat ?: return
                val lng = session.singleLng ?: return
                // Show the point; the user re-starts injection from the foreground.
                mockStateRepository.setCurrentMockLocation(LatLng(lat, lng))
            }
        }
    }
}
