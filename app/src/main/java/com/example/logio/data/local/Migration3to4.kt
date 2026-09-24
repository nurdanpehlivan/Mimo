package com.example.logio.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_3_TO_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // audioPath alanını ekle
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN audioPath TEXT")

        // calories, protein, carbs, fat alanlarını ekle
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN calories INTEGER")
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN protein REAL")
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN carbs REAL")
        db.execSQL("ALTER TABLE journal_entries ADD COLUMN fat REAL")
    }
}


