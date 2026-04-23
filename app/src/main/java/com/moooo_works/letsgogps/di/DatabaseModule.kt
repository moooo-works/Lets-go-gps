package com.moooo_works.letsgogps.di

import android.content.Context
import androidx.room.Room
import com.moooo_works.letsgogps.data.local.AppDatabase
import com.moooo_works.letsgogps.data.local.LocationDao
import com.moooo_works.letsgogps.data.local.RouteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, "mockgps.db")
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .build()
    }

    @Provides
    fun provideLocationDao(db: AppDatabase): LocationDao = db.locationDao()

    @Provides
    fun provideRouteDao(db: AppDatabase): RouteDao = db.routeDao()
}
