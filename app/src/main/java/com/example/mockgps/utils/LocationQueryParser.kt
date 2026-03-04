package com.example.mockgps.utils

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
    fun parse(input: String): ParseResult {
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
        if (OpenLocationCode.isValidCode(trimmed)) {
            if (OpenLocationCode.isFullCode(trimmed)) {
                try {
                    val code = OpenLocationCode(trimmed)
                    val decode = code.decode()
                    return ParseResult.Success(ParsedLocation(LatLng(decode.centerLatitude, decode.centerLongitude), LocationSource.PLUS_CODE))
                } catch (e: IllegalArgumentException) {
                    return ParseResult.Error("Failed to decode Plus Code")
                }
            } else {
                return ParseResult.Error("Short Plus Codes are not supported yet")
            }
        }

        return ParseResult.Error("Invalid format. Please enter lat,lng or a full Plus Code")
    }
}
