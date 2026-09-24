package com.example.logio.data.local

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromEntryType(value: EntryType): String {
        return value.name
    }

    @TypeConverter
    fun toEntryType(value: String): EntryType {
        return runCatching { EntryType.valueOf(value) }.getOrDefault(EntryType.TEXT)
    }
}