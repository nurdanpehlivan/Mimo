package com.example.logio.ui.timeline

import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import java.text.SimpleDateFormat
import java.util.*
fun isSameCalendarDay(timeMillisA: Long, timeMillisB: Long): Boolean {
    val calA = Calendar.getInstance().apply { timeInMillis = timeMillisA }
    val calB = Calendar.getInstance().apply { timeInMillis = timeMillisB }
    return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
            calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
}

fun formatTodayLabel(): String {
    val now = Calendar.getInstance().time
    return SimpleDateFormat("dd MMMM yyyy", Locale("tr")).format(now)
}

fun getWaterTotalForDay(entries: List, dayMillis: Long): Int {
    val today = Calendar.getInstance().apply { timeInMillis = dayMillis }
    return entries
        .filter { entry ->
            entry.entryType == EntryType.WATER &&
                    entry.waterAmountMl != null &&
                    isSameCalendarDay(entry.createdAt, today.timeInMillis)
        }
        .sumOf { it.waterAmountMl ?: 0 }
}