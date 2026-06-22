package com.moooo_works.letsgogps.domain

import com.google.gson.Gson
import com.moooo_works.letsgogps.domain.repository.MockStatus
import kotlin.math.abs

/** A lat/lng pair we control the serialization of (rather than the GMS LatLng). */
data class SerLatLng(val lat: Double, val lng: Double)

enum class MockSessionMode { SINGLE, ROUTE }

/**
 * The minimal mock state persisted across process death so the app can restore
 * (and optionally resume) when it returns to the foreground. Serialized to
 * DataStore as JSON. See [decideRestore] for how it is acted on.
 */
data class PersistedMockSession(
    val mode: MockSessionMode,
    val savedAtMillis: Long,      // wall-clock save time, for the 24h TTL
    val bootTimeMillis: Long,     // currentTimeMillis - elapsedRealtime, ~constant within one boot
    val wasInjecting: Boolean,    // were we actively injecting when saved (vs paused)
    // SINGLE
    val singleLat: Double? = null,
    val singleLng: Double? = null,
    // ROUTE
    val waypoints: List<SerLatLng> = emptyList(),
    val speedMps: Double = 0.0,
    val loopMode: String = "NONE",
    val segmentIndex: Int = 0,
    val distanceCoveredInSegment: Double = 0.0,
    val isReturning: Boolean = false,
)

enum class RestoreDecision {
    /** Same boot, still injecting, fresh: re-inject and resume playback. */
    RESTORE_AND_RESUME,
    /** Rebooted, or paused-when-killed: show the route/point but do not auto-inject. */
    RESTORE_PAUSED,
    /** Expired: drop it. */
    DISCARD,
    /** Nothing to do (no session, or memory already has a live session). */
    NO_OP,
}

/**
 * Pure decision for what to do with a persisted session when the app returns to
 * the foreground. Kept free of Android types so it is unit-testable.
 *
 * @param memoryHasActiveSession true if the in-memory singletons still hold a
 *   live session (process was NOT killed) — then we must not double-restore.
 * @param currentBootTimeMillis  System.currentTimeMillis() - SystemClock.elapsedRealtime()
 */
fun decideRestore(
    persisted: PersistedMockSession?,
    memoryHasActiveSession: Boolean,
    currentBootTimeMillis: Long,
    nowMillis: Long,
    // ponytail: boot-time math jitters by a few ms; 5s tolerance. Widen if false reboots show up.
    bootToleranceMillis: Long = 5_000,
    ttlMillis: Long = 24L * 60 * 60 * 1000,
): RestoreDecision = when {
    persisted == null -> RestoreDecision.NO_OP
    memoryHasActiveSession -> RestoreDecision.NO_OP
    nowMillis - persisted.savedAtMillis > ttlMillis -> RestoreDecision.DISCARD
    // Paused-when-killed: show it, but never auto-inject — the user had stopped.
    !persisted.wasInjecting -> RestoreDecision.RESTORE_PAUSED
    abs(currentBootTimeMillis - persisted.bootTimeMillis) <= bootToleranceMillis ->
        RestoreDecision.RESTORE_AND_RESUME
    // Rebooted: show route + progress, wait for the user to press play.
    else -> RestoreDecision.RESTORE_PAUSED
}

/**
 * Whether the simulator going IDLE represents a route that finished on its own
 * (so the recovery session should be cleared), as opposed to IDLE caused by an
 * explicit stop / teardown. [isStopping] is set by the service during handleStop;
 * without it, stop()'s IDLE could be misread as "route completed" and wipe the
 * session that a killed process needs to recover.
 */
fun isRouteCompletionOnIdle(isStopping: Boolean, status: MockStatus): Boolean =
    !isStopping && (status == MockStatus.ROUTE_PLAYING || status == MockStatus.ROUTE_PAUSED)

/** Defensive JSON → session: blank or corrupt data yields null, never throws. */
fun parseMockSession(json: String?, gson: Gson): PersistedMockSession? =
    if (json.isNullOrBlank()) null
    else try {
        gson.fromJson(json, PersistedMockSession::class.java)
    } catch (e: Exception) {
        null
    }
