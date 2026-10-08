/*
 * TimelineUtils: Kayıtlarla ilgili küçük yardımcı fonksiyonlar.
 *
 * Aynı gün mü kontrolü, uyku değerinin makul olup olmadığı (en fazla 16 saat), adım metninden
 * sayıyı okuma, günlük su toplamı, kart başlığı/içeriği ve harcama tutarı biçimlendirme.
 */
package com.example.logio.ui.timeline

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.logio.R
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import java.util.*

fun isSameCalendarDay(timeMillisA: Long, timeMillisB: Long): Boolean {
    val calA = Calendar.getInstance().apply { timeInMillis = timeMillisA }
    val calB = Calendar.getInstance().apply { timeInMillis = timeMillisB }
    return calA.get(Calendar.YEAR) == calB.get(Calendar.YEAR) &&
            calA.get(Calendar.DAY_OF_YEAR) == calB.get(Calendar.DAY_OF_YEAR)
}


// Bir gecelik makul uyku üst sınırı; üstündeki değerler (örn. 8,5 yerine yazılan 85) yok sayılır
const val MAX_SLEEP_HOURS = 16.0

fun isPlausibleSleep(hours: Double?): Boolean = hours != null && hours > 0.0 && hours <= MAX_SLEEP_HOURS

// Health Connect adım kayıtlarında sayı yalnızca metinde ("Bugün 4250 adım attınız.").
// Metin kaydedildiği dilde olduğu için kelimeye değil, metindeki ilk sayıya bakılır.
private val StepsInTextRegex = Regex("""(\d[\d.]*)""")

fun parseStepCount(content: String): Int? =
    StepsInTextRegex.find(content)?.groupValues?.get(1)?.replace(".", "")?.toIntOrNull()

fun getWaterTotalForDay(entries: List<JournalEntryEntity>, dayMillis: Long): Int {
    val today = Calendar.getInstance().apply { timeInMillis = dayMillis }
    return entries
        .filter { entry ->
            entry.entryType == EntryType.WATER &&
                    entry.waterAmountMl != null &&
                    isSameCalendarDay(entry.createdAt, today.timeInMillis)
        }
        .sumOf { it.waterAmountMl ?: 0 }
}

fun isBrokenTemplateText(value: String): Boolean {
    return value.contains("\\(") || value.contains("₺null")
}

@Composable
fun displayEntryTitle(entry: JournalEntryEntity): String? {
    val title = entry.title.trim()
    if (title.isBlank() || isBrokenTemplateText(title)) {
        return when (entry.entryType) {
            EntryType.EXPENSE -> stringResource(R.string.action_expense)
            else -> null
        }
    }
    return title
}

fun displayEntryContent(entry: JournalEntryEntity): String? {
    if (entry.entryType == EntryType.TODO) return null
    val content = entry.content.trim()
    if (content.isBlank() || isBrokenTemplateText(content)) return null
    return content
}

fun formatExpenseAmountLabel(amount: Double?): String? {
    if (amount == null) return null
    return if (amount % 1.0 == 0.0) {
        "₺${amount.toInt()}"
    } else {
        "₺${"%.2f".format(amount)}"
    }
}
