package com.example.logio.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [JournalEntryEntity::class],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class LogioDatabase : RoomDatabase() {

    abstract fun journalDao(): JournalDao
}