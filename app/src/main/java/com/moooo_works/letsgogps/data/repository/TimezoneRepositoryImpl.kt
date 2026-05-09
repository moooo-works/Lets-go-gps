package com.moooo_works.letsgogps.data.repository

import com.google.gson.JsonParser
import com.moooo_works.letsgogps.domain.repository.TimezoneRepository
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class TimezoneRepositoryImpl @Inject constructor() : TimezoneRepository {

    /**
     * Calls timeapi.io's coordinate-to-timezone endpoint. Free, no API key.
     * Failure (network/timeout/parse) returns null so callers don't surface
     * a misleading "mismatch" warning when we simply couldn't resolve.
     */
    override suspend fun resolveTimezone(lat: Double, lng: Double): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://timeapi.io/api/TimeZone/coordinate?latitude=$lat&longitude=$lng")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", USER_AGENT)
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val tz = JsonParser.parseString(body).asJsonObject
                .get("timeZone")?.asString
            tz?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        private const val USER_AGENT = "MockGPS/1.0"
    }
}
