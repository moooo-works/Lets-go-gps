package com.moooo_works.letsgogps.service

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.ContextCompat
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
import dagger.hilt.android.qualifiers.ApplicationContext
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
 */
@Singleton
class MockSessionRestorer @Inject constructor(
    @ApplicationContext private val context: Context,
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
            RestoreDecision.RESTORE_AND_RESUME -> persisted?.let { restore(it, resume = true) }
            RestoreDecision.RESTORE_PAUSED -> persisted?.let { restore(it, resume = false) }
        }
    }

    private fun restore(session: com.moooo_works.letsgogps.domain.PersistedMockSession, resume: Boolean) {
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
                if (resume) {
                    // handleStartRoute calls play(), which continues from the restored progress.
                    startService(MockLocationService.ACTION_START_ROUTE)
                } else {
                    // Device rebooted: show route + progress, wait for the user to press play.
                    mockStateRepository.setMockStatus(MockStatus.ROUTE_PAUSED)
                }
            }
            MockSessionMode.SINGLE -> {
                val lat = session.singleLat ?: return
                val lng = session.singleLng ?: return
                mockStateRepository.setCurrentMockLocation(LatLng(lat, lng))
                if (resume) {
                    startService(MockLocationService.ACTION_START_SINGLE) {
                        putExtra(MockLocationService.EXTRA_LAT, lat)
                        putExtra(MockLocationService.EXTRA_LNG, lng)
                    }
                }
                // RESTORE_PAUSED for a single point: location is shown, no injection.
            }
        }
    }

    private fun startService(action: String, configure: Intent.() -> Unit = {}) {
        val intent = Intent(context, MockLocationService::class.java).apply {
            this.action = action
            configure()
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
