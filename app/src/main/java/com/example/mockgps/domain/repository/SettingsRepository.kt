package com.example.mockgps.domain.repository

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
}
