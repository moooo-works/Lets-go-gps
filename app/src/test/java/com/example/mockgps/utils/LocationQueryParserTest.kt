package com.example.mockgps.utils

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest=Config.NONE)
class LocationQueryParserTest {

    @Test
    fun parse_latLng_commaSeparated_success() {
        val result = LocationQueryParser.parse("25.0330, 121.5654")
        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(25.0330, success.parsedLocation.latLng.latitude, 0.0001)
        assertEquals(121.5654, success.parsedLocation.latLng.longitude, 0.0001)
        assertEquals(LocationSource.LAT_LNG, success.parsedLocation.source)
    }

    @Test
    fun parse_latLng_spaceSeparated_success() {
        val result = LocationQueryParser.parse("25.0330 121.5654")
        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(25.0330, success.parsedLocation.latLng.latitude, 0.0001)
        assertEquals(121.5654, success.parsedLocation.latLng.longitude, 0.0001)
    }

    @Test
    fun parse_latLng_negativeCoordinates_success() {
        val result = LocationQueryParser.parse("-34.6037, -58.3816")
        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(-34.6037, success.parsedLocation.latLng.latitude, 0.0001)
        assertEquals(-58.3816, success.parsedLocation.latLng.longitude, 0.0001)
    }

    @Test
    fun parse_latLng_outOfBoundsLat_error() {
        val result = LocationQueryParser.parse("95.0, 120.0")
        assertTrue(result is ParseResult.Error)
        assertEquals("Coordinates out of bounds: lat [-90, 90], lng [-180, 180]", (result as ParseResult.Error).message)
    }

    @Test
    fun parse_latLng_outOfBoundsLng_error() {
        val result = LocationQueryParser.parse("25.0, 190.0")
        assertTrue(result is ParseResult.Error)
        assertEquals("Coordinates out of bounds: lat [-90, 90], lng [-180, 180]", (result as ParseResult.Error).message)
    }

    @Test
    fun parse_plusCode_full_success() {
        val code = "849VCWC8+R9"
        val result = LocationQueryParser.parse(code)
        if (result is ParseResult.Error) {
            println("ERROR: " + result.message)
        }
        assertTrue(result is ParseResult.Success)
        val success = result as ParseResult.Success
        assertEquals(LocationSource.PLUS_CODE, success.parsedLocation.source)
        // Approximate location of 849VCWC8+R9
        assertEquals(37.4220, success.parsedLocation.latLng.latitude, 0.01)
        assertEquals(-122.0841, success.parsedLocation.latLng.longitude, 0.01)
    }

    @Test
    fun parse_plusCode_short_error() {
        val result = LocationQueryParser.parse("2QRC+9G")
        assertTrue(result is ParseResult.Error)
        assertEquals("Short Plus Codes are not supported yet", (result as ParseResult.Error).message)
    }

    @Test
    fun parse_invalidFormat_error() {
        val result = LocationQueryParser.parse("abc, def")
        assertTrue(result is ParseResult.Error)
        assertEquals("Invalid format. Please enter lat,lng or a full Plus Code", (result as ParseResult.Error).message)
    }

    @Test
    fun parse_emptyInput_error() {
        val result = LocationQueryParser.parse("   ")
        assertTrue(result is ParseResult.Error)
        assertEquals("Input cannot be empty", (result as ParseResult.Error).message)
    }
}
