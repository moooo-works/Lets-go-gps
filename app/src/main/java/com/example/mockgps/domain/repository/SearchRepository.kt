package com.example.mockgps.domain.repository

import com.google.android.gms.maps.model.LatLng

data class GeocodedLocation(
    val name: String,
    val address: String,
    val latLng: LatLng
)

interface SearchRepository {
    suspend fun search(query: String): Result<List<GeocodedLocation>>
}
