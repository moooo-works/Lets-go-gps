package com.moooo_works.letsgogps.di

import com.moooo_works.letsgogps.data.engine.AndroidLocationMockEngine
import com.moooo_works.letsgogps.data.repository.LocationRepositoryImpl
import com.moooo_works.letsgogps.data.repository.MockStateRepositoryImpl
import com.moooo_works.letsgogps.data.repository.ProRepositoryImpl
import com.moooo_works.letsgogps.data.repository.SearchRepositoryImpl
import com.moooo_works.letsgogps.data.repository.SettingsRepositoryImpl
import com.moooo_works.letsgogps.domain.LocationMockEngine
import com.moooo_works.letsgogps.domain.repository.LocationRepository
import com.moooo_works.letsgogps.domain.repository.MockStateRepository
import com.moooo_works.letsgogps.domain.repository.ProRepository
import com.moooo_works.letsgogps.domain.repository.SearchRepository
import com.moooo_works.letsgogps.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        locationRepositoryImpl: LocationRepositoryImpl
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindLocationMockEngine(
        androidLocationMockEngine: AndroidLocationMockEngine
    ): LocationMockEngine

    @Binds
    @Singleton
    abstract fun bindMockStateRepository(
        mockStateRepositoryImpl: MockStateRepositoryImpl
    ): MockStateRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSearchRepository(
        searchRepositoryImpl: SearchRepositoryImpl
    ): SearchRepository

    @Binds
    @Singleton
    abstract fun bindProRepository(
        proRepositoryImpl: ProRepositoryImpl
    ): ProRepository
}
