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
}
