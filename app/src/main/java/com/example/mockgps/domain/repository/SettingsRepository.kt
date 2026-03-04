package com.example.mockgps.domain.repository

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeLastCenter(): Flow<LatLng?>
    suspend fun setLastCenter(latLng: LatLng)
}
