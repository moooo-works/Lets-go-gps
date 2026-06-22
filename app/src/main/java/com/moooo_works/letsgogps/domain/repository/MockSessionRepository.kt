package com.moooo_works.letsgogps.domain.repository

import com.moooo_works.letsgogps.domain.PersistedMockSession

/**
 * Persists the active mock session across process death so it can be restored
 * (and optionally resumed) when the app returns to the foreground.
 */
interface MockSessionRepository {
    suspend fun save(session: PersistedMockSession)
    suspend fun load(): PersistedMockSession?
    suspend fun clear()
}
