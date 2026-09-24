package com.example.logio.di

import android.content.Context
import androidx.room.Room
import com.example.logio.data.local.JournalDao
import com.example.logio.data.local.LogioDatabase
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
    fun provideLogioDatabase(
        @ApplicationContext context: Context
    ): LogioDatabase {
        return Room.databaseBuilder(
            context,
            LogioDatabase::class.java,
            "logio_database.db"
        )
            .fallbackToDestructiveMigration() // Sürüm değiştiğinde çakışmaları önlemek için
            .build()
    }

    @Provides
    @Singleton
    fun provideJournalDao(database: LogioDatabase): JournalDao {
        return database.journalDao()
    }
}