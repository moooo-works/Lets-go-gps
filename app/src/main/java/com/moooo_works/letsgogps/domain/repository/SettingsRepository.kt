package com.moooo_works.letsgogps.domain.repository

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeLastCenter(): Flow<LatLng?>
    suspend fun setLastCenter(latLng: LatLng)

    fun observeAltitude(): Flow<Double>
    suspend fun setAltitude(altitude: Double)

    fun observeRandomAltitude(): Flow<Boolean>
    suspend fun setRandomAltitude(enabled: Boolean)

    fun observeCoordinateJitter(): Flow<Boolean>
    suspend fun setCoordinateJitter(enabled: Boolean)

    fun observeRouteCornerSlowdown(): Flow<Boolean>
    suspend fun setRouteCornerSlowdown(enabled: Boolean)

    fun observeRouteSpeed(): Flow<Double>
    suspend fun setRouteSpeed(value: Double)

    fun observeTransportMode(): Flow<String>
    suspend fun setTransportMode(name: String)

    fun observeMapMode(): Flow<String>
    suspend fun setMapMode(name: String)

    fun observeMapType(): Flow<String>
    suspend fun setMapType(name: String)

    fun hasSeenOnboarding(): Flow<Boolean>
    suspend fun setOnboardingDone()

    /** Returns the feature-tip version the user has already acknowledged (0 = never). */
    fun getLoopBounceTipSeenVersion(): Flow<Int>
    suspend fun setLoopBounceTipSeen(version: Int)

    fun hasSeenSortTip(): Flow<Boolean>
    suspend fun setSortTipSeen()

    fun observeClipboardHintEnabled(): Flow<Boolean>
    suspend fun setClipboardHintEnabled(enabled: Boolean)

    fun hasSeenClipboardHintTip(): Flow<Boolean>
    suspend fun setClipboardHintTipSeen()

    fun hasSeenFolderTip(): Flow<Boolean>
    suspend fun setFolderTipSeen()

    fun hasSeenGpxTip(): Flow<Boolean>
    suspend fun setGpxTipSeen()
    fun hasSeenJumpModeTip(): Flow<Boolean>
    suspend fun setJumpModeTipSeen()

    /** Whether to warn the user when mock-location time-zone differs from system. */
    fun observeEnableTimezoneCheck(): Flow<Boolean>
    suspend fun setEnableTimezoneCheck(enabled: Boolean)

    // ── Health Connect 步數同步 ───────────────────────────────────────────

    /** Whether simulated travel distance is written to Health Connect as steps. */
    fun observeStepSyncEnabled(): Flow<Boolean>
    suspend fun setStepSyncEnabled(enabled: Boolean)

    /** Metres per step, used to convert distance into a step count. */
    fun observeStepLengthMeters(): Flow<Double>
    suspend fun setStepLengthMeters(meters: Double)

    /** Maximum steps that may be written per day. */
    fun observeStepDailyQuota(): Flow<Int>
    suspend fun setStepDailyQuota(quota: Int)

    /**
     * Steps already written today. Buckets by date internally — reading on a new
     * day yields 0 without any caller-side reset.
     */
    fun observeStepQuotaUsedToday(): Flow<Int>

    /** Add to today's used quota, rolling the bucket over if the date changed. */
    suspend fun addStepQuotaUsed(steps: Int)
}
