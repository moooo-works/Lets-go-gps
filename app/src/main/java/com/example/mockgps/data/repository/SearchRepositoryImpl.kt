package com.example.mockgps.data.repository

import com.example.mockgps.domain.repository.GeocodedLocation
import com.example.mockgps.domain.repository.SearchRepository
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val gson: Gson
) : SearchRepository {

    override suspend fun search(query: String): Result<List<GeocodedLocation>> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            // Using OpenStreetMap Nominatim API (Free, no key required for low volume)
            val urlString = "https://nominatim.openstreetmap.org/search?q=$encodedQuery&format=json&addressdetails=1&limit=10"
            val url = URL(urlString)
            
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                // Nominatim requires a User-Agent header
                setRequestProperty("User-Agent", "MockGPS-Android-App")
                connectTimeout = 5000
                readTimeout = 5000
            }

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val itemType = object : TypeToken<List<NominatimResponse>>() {}.type
                val results: List<NominatimResponse> = gson.fromJson(response, itemType)
                
                val locations = results.map {
                    GeocodedLocation(
                        name = it.display_name.split(",").firstOrNull() ?: "Unknown",
                        address = it.display_name,
                        latLng = LatLng(it.lat.toDouble(), it.lon.toDouble())
                    )
                }
                Result.success(locations)
            } else {
                Result.failure(Exception("HTTP error: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Nominatim API response structure
    internal data class NominatimResponse(
        val lat: String,
        val lon: String,
        val display_name: String,
        val type: String
    )
}
