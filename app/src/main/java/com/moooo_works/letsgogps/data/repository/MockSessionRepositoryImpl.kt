package com.moooo_works.letsgogps.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.moooo_works.letsgogps.domain.PersistedMockSession
import com.moooo_works.letsgogps.domain.parseMockSession
import com.moooo_works.letsgogps.domain.repository.MockSessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Separate DataStore file from "settings"; one key holds the whole session as JSON.
private val Context.mockSessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "mock_session")

@Singleton
class MockSessionRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson,
) : MockSessionRepository {

    private val dataStore = context.mockSessionDataStore

    override suspend fun save(session: PersistedMockSession) {
        val json = gson.toJson(session)
        dataStore.edit { it[KEY_SESSION] = json }
    }

    override suspend fun load(): PersistedMockSession? {
        val json = dataStore.data.map { it[KEY_SESSION] }.first()
        return parseMockSession(json, gson)
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(KEY_SESSION) }
    }

    companion object {
        private val KEY_SESSION = stringPreferencesKey("mock_session_json")
    }
}
