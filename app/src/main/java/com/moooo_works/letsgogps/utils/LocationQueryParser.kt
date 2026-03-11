package com.moooo_works.letsgogps.utils

import com.google.android.gms.maps.model.LatLng
import com.google.openlocationcode.OpenLocationCode

enum class LocationSource {
    PLUS_CODE, LAT_LNG
}

data class ParsedLocation(val latLng: LatLng, val source: LocationSource)

sealed class ParseResult {
    data class Success(val parsedLocation: ParsedLocation) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

object LocationQueryParser {
    fun parse(input: String, referenceLatLng: LatLng? = null): ParseResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return ParseResult.Error("Input cannot be empty")
        }

        // Try lat,lng or lat lng
        val latLngRegex = """^([-+]?\d+(\.\d+)?)(?:\s*,\s*|\s+)([-+]?\d+(\.\d+)?)$""".toRegex()
        val matchResult = latLngRegex.find(trimmed)
        if (matchResult != null) {
            val latStr = matchResult.groupValues[1]
            val lngStr = matchResult.groupValues[3]
            try {
                val lat = latStr.toDouble()
                val lng = lngStr.toDouble()
                if (lat !in -90.0..90.0 || lng !in -180.0..180.0) {
                    return ParseResult.Error("Coordinates out of bounds: lat [-90, 90], lng [-180, 180]")
                }
                return ParseResult.Success(ParsedLocation(LatLng(lat, lng), LocationSource.LAT_LNG))
            } catch (e: NumberFormatException) {
                return ParseResult.Error("Invalid coordinate format")
            }
        }

        // Try Plus Code
        // Extract the first token that might be a Plus Code, ignoring trailing text
        val token = trimmed.split("\\s+".toRegex()).firstOrNull() ?: ""

        if (OpenLocationCode.isValidCode(token)) {
            if (OpenLocationCode.isFullCode(token)) {
                try {
                    val code = OpenLocationCode(token)
                    val decode = code.decode()
                    return ParseResult.Success(ParsedLocation(LatLng(decode.centerLatitude, decode.centerLongitude), LocationSource.PLUS_CODE))
                } catch (e: IllegalArgumentException) {
                    return ParseResult.Error("Failed to decode Plus Code")
                }
            } else if (OpenLocationCode.isShortCode(token)) {
                if (referenceLatLng == null) {
                    return ParseResult.Error("Reference location required for short Plus Code")
                }
                try {
                    val code = OpenLocationCode(token)
                    val recovered = code.recover(referenceLatLng.latitude, referenceLatLng.longitude)
                    val decode = recovered.decode()
                    return ParseResult.Success(ParsedLocation(LatLng(decode.centerLatitude, decode.centerLongitude), LocationSource.PLUS_CODE))
                } catch (e: IllegalArgumentException) {
                    return ParseResult.Error("Failed to recover short Plus Code: ${e.message}")
                }
            } else {
                return ParseResult.Error("Invalid Plus Code type")
            }
        }

        return ParseResult.Error("Invalid format. Please enter lat,lng or a valid Plus Code")
    }
}
