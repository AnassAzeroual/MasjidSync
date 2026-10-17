package com.mosquetracker.app.di

import android.content.Context
import androidx.room.Room
import com.mosquetracker.app.data.local.AppDatabase
import com.mosquetracker.app.data.local.AttendanceDao
import com.mosquetracker.app.data.local.MosqueDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*AppDatabase.ALL_MIGRATIONS)
            .build()

    @Provides
    fun provideMosqueDao(database: AppDatabase): MosqueDao = database.mosqueDao()

    @Provides
    fun provideAttendanceDao(database: AppDatabase): AttendanceDao = database.attendanceDao()
}
