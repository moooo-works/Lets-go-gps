package com.example.mockgps.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.mockgps.domain.repository.SettingsRepository
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private val dataStore = context.dataStore

    companion object {
        val LAST_CENTER_LAT = doublePreferencesKey("last_center_lat")
        val LAST_CENTER_LNG = doublePreferencesKey("last_center_lng")
    }

    override fun observeLastCenter(): Flow<LatLng?> {
        return dataStore.data.map { preferences ->
            val lat = preferences[LAST_CENTER_LAT]
            val lng = preferences[LAST_CENTER_LNG]
            if (lat != null && lng != null) {
                LatLng(lat, lng)
            } else {
                null
            }
        }
    }

    override suspend fun setLastCenter(latLng: LatLng) {
        dataStore.edit { preferences ->
            preferences[LAST_CENTER_LAT] = latLng.latitude
            preferences[LAST_CENTER_LNG] = latLng.longitude
        }
    }
}
