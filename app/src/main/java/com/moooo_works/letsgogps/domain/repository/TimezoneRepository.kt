package com.moooo_works.letsgogps.domain.repository

interface TimezoneRepository {
    /**
     * Resolves the IANA time-zone id (e.g. "Asia/Taipei") for the given
     * geographic coordinates. Returns null on network failure or unknown
     * coordinate — callers should treat null as "skip the warning, can't
     * tell" rather than as a mismatch.
     */
    suspend fun resolveTimezone(lat: Double, lng: Double): String?
}
