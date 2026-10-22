/*
 * EntryLocalizer: Uygulamanın kendi yazdığı kayıt metinlerini ("250 ml su içildi.", "Su Tüketimi") ekranın o anki diline çevirir.
 *
 * Bu metinler veritabanına kaydın eklendiği andaki dille yazılır. Dil sonradan değişince eski dilde kalmasınlar diye
 * gösterilirken tanınırlar: metin desteklenen dillerden birinin şablonuna uyuyorsa, içindeki değerlerle (ml, adım, saat...)
 * şu anki dilde yeniden oluşturulur. Kullanıcının kendi yazdığı başlık ve notlar hiçbir şablona uymadığı için aynen kalır.
 */
package com.example.logio.ui.timeline

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.logio.R
import com.example.logio.data.local.EntryType
import com.example.logio.data.local.JournalEntryEntity
import com.example.logio.util.AppLanguage
import com.example.logio.util.JourneyTemplate
import java.util.Locale

/** Ekranın diline göre hazırlanmış bir [EntryLocalizer]; dil değişince yenisi oluşturulur. */
@Composable
fun rememberEntryLocalizer(): EntryLocalizer {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(configuration) { EntryLocalizer.create(context) }
}

class EntryLocalizer(
    private val current: Resources,
    private val all: List<Resources>
) {
    /** Bir tür adı: isteğe bağlı emoji + metin kaynağı ("🍔 Yemek", "Su Tüketimi"). */
    private data class Label(@StringRes val res: Int, val emoji: String? = null)

    private val locale: Locale = current.configuration.locales[0]

    fun title(entry: JournalEntryEntity): String {
        val title = entry.title.trim()
        return relabel(title, titleLabels(entry.entryType)) ?: title
    }

    fun content(entry: JournalEntryEntity): String {
        val content = entry.content.trim()
        val localized = when (entry.entryType) {
            EntryType.MOOD -> retemplate(content, R.string.entry_mood_content)
            EntryType.WATER -> retemplate(content, R.string.entry_water_content)
            EntryType.STEPS -> retemplate(content, R.string.entry_steps_content)
            // Uyku metninin sonuna kullanıcının notu eklenmiş olabilir: o kısım aynen korunur
            EntryType.SLEEP -> retemplate(content, R.string.entry_sleep_manual, keepSuffix = true)
                ?: retemplate(content, R.string.entry_sleep_health, keepSuffix = true)
            EntryType.DRAW -> relabel(content, listOf(Label(R.string.entry_drawing_content)))
            EntryType.FILM -> retemplate(content, R.string.entry_film_content)
            EntryType.BOOK -> retemplate(content, R.string.entry_book_content)
            EntryType.MUSIC -> retemplate(content, R.string.entry_music_content)
            EntryType.COFFEE -> replural(content, R.plurals.cups_count)
            EntryType.ALCOHOL -> replural(content, R.plurals.glasses_count)
            EntryType.EXPENSE -> relabel(content, expenseLabels)
            EntryType.JOURNEY_VIDEO -> journeyContent(content)
            else -> null
        }
        return localized ?: content
    }

    private fun titleLabels(type: EntryType): List<Label> = when (type) {
        EntryType.TEXT -> listOf(Label(R.string.action_note))
        EntryType.MOOD -> listOf(Label(R.string.action_mood))
        EntryType.PHOTO -> listOf(Label(R.string.dlg_photo_memory))
        EntryType.VIDEO -> listOf(Label(R.string.dlg_video_memory))
        EntryType.TODO -> listOf(Label(R.string.action_todo))
        EntryType.VOICE -> listOf(Label(R.string.type_voice))
        EntryType.LINK -> listOf(Label(R.string.action_link))
        EntryType.LOCATION -> listOf(Label(R.string.entry_selected_location))
        EntryType.DRAW -> listOf(Label(R.string.action_draw))
        EntryType.SLEEP -> listOf(Label(R.string.action_sleep), Label(R.string.entry_night_sleep))
        EntryType.WATER -> listOf(Label(R.string.dlg_water))
        EntryType.STEPS -> listOf(Label(R.string.entry_steps_title))
        EntryType.MEAL -> listOf(Label(R.string.type_meal))
        EntryType.EXPENSE -> expenseLabels
        EntryType.COFFEE -> listOf(Label(R.string.action_coffee)) + CoffeeKind.entries.map { Label(it.label) }
        EntryType.ALCOHOL -> listOf(Label(R.string.action_alcohol)) + DrinkKind.entries.map { Label(it.label) }
        EntryType.JOURNEY_VIDEO -> listOf(Label(R.string.journey_manual_title)) + JourneyTemplate.entries.map { Label(it.label) }
        else -> emptyList()
    }

    private val expenseLabels = ExpenseCategory.entries.map { Label(it.label, it.emoji) }

    /** Metin, adlardan birinin herhangi bir dildeki hali mi? Öyleyse şu anki dildeki halini döndürür. */
    private fun relabel(text: String, labels: List<Label>): String? {
        if (text.isEmpty()) return null
        for (label in labels) {
            val matches = all.any { res -> text == label.render(res) }
            if (matches) return label.render(current)
        }
        return null
    }

    private fun Label.render(res: Resources): String =
        if (emoji == null) res.getString(this.res) else "$emoji ${res.getString(this.res)}"

    private fun retemplate(text: String, @StringRes id: Int, keepSuffix: Boolean = false): String? {
        if (text.isEmpty()) return null
        for (res in all) {
            val match = TemplateText.match(text, res.getString(id), keepSuffix) ?: continue
            val rebuilt = TemplateText.fill(current.getString(id), match.args, locale)
            return if (match.suffix.isBlank()) rebuilt else rebuilt + match.suffix
        }
        return null
    }

    private fun replural(text: String, @PluralsRes id: Int): String? {
        if (text.isEmpty()) return null
        for (res in all) {
            for (format in pluralForms(res, id)) {
                val count = TemplateText.match(text, format, keepSuffix = false)?.args?.singleOrNull()?.toIntOrNull() ?: continue
                return current.getQuantityString(id, count, count)
            }
        }
        return null
    }

    /** "Haftalık Özet · 12 fotoğraf": şablon adı ve fotoğraf sayısı ayrı ayrı çevrilir. */
    private fun journeyContent(text: String): String? {
        val parts = text.split(" · ", limit = 2)
        if (parts.size != 2) return null
        val label = relabel(parts[0], JourneyTemplate.entries.map { Label(it.label) }) ?: return null
        val photos = replural(parts[1], R.plurals.photo_count) ?: return null
        return "$label · $photos"
    }

    // Bir çoğul kaynağın tüm biçimleri ("%d cup", "%d cups"...): dillerin çoğul kuralları farklı olduğu için birkaç sayı denenir
    private fun pluralForms(res: Resources, @PluralsRes id: Int): Set<String> =
        listOf(0, 1, 2, 3, 5, 11, 21, 100).mapTo(LinkedHashSet()) { res.getQuantityString(id, it) }

    companion object {
        fun create(context: Context): EntryLocalizer {
            val base = context.resources.configuration
            val languages = AppLanguage.entries.filter { it != AppLanguage.SYSTEM }.map { language ->
                val config = Configuration(base).apply { setLocale(Locale.forLanguageTag(language.tag)) }
                context.createConfigurationContext(config).resources
            }
            return EntryLocalizer(context.resources, languages)
        }
    }
}

/**
 * Biçim metinleriyle ("Drank %d ml of water.") eşleştirme: metinden değerleri çıkarır ve başka dilin biçimine yerleştirir.
 * Android'e bağlı değildir; birim testlerle denenir.
 */
internal object TemplateText {
    class Match(val args: List<String>, val suffix: String)

    private val Spec = Regex("""%(?:(\d+)\$)?([sd])""")

    /** [text] biçime uyuyorsa yer tutuculardaki değerleri sırasıyla (%1$, %2$ ...) döndürür. */
    fun match(text: String, format: String, keepSuffix: Boolean): Match? {
        val pattern = StringBuilder("^")
        val positions = mutableListOf<Int>()
        var last = 0
        Spec.findAll(format).forEachIndexed { index, spec ->
            pattern.append(Regex.escape(format.substring(last, spec.range.first)))
            pattern.append(if (spec.groupValues[2] == "d") """(\d+)""" else "(.*?)")
            positions += spec.groupValues[1].toIntOrNull() ?: (index + 1)
            last = spec.range.last + 1
        }
        pattern.append(Regex.escape(format.substring(last)))
        pattern.append(if (keepSuffix) "(.*)$" else "$")

        val result = Regex(pattern.toString(), RegexOption.DOT_MATCHES_ALL).find(text) ?: return null
        val args = MutableList(positions.maxOrNull() ?: 0) { "" }
        positions.forEachIndexed { i, position -> args[position - 1] = result.groupValues[i + 1] }
        val suffix = if (keepSuffix) result.groupValues.last() else ""
        return Match(args, suffix)
    }

    /** Değerleri biçime yerleştirir; yalnızca rakamdan oluşan değerler %d için sayıya çevrilir. */
    fun fill(format: String, args: List<String>, locale: Locale): String {
        val typed: Array<Any> = args.map { it.toLongOrNull() ?: it }.toTypedArray()
        return String.format(locale, format, *typed)
    }
}
