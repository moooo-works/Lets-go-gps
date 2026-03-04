package com.example.mockgps.di

import com.example.mockgps.data.engine.AndroidLocationMockEngine
import com.example.mockgps.data.repository.LocationRepositoryImpl
import com.example.mockgps.domain.LocationMockEngine
import com.example.mockgps.domain.repository.LocationRepository
import com.example.mockgps.data.repository.MockStateRepositoryImpl
import com.example.mockgps.domain.repository.MockStateRepository
import com.example.mockgps.data.repository.SettingsRepositoryImpl
import com.example.mockgps.domain.repository.SettingsRepository
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
}
