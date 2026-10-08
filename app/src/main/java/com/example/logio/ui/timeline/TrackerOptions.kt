package com.example.logio.ui.timeline

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.example.logio.R

/**
 * Harcama kategorisi. Veritabanına [key] yazılır (dil değişse de sabit kalır), ekranda [label] gösterilir.
 * Renkler istatistikteki dağılım çubuğunda kullanılır.
 */
enum class ExpenseCategory(val key: String, @StringRes val label: Int, val emoji: String, val color: Color) {
    FOOD("food", R.string.exp_food, "🍔", Color(0xFFF97316)),
    TRANSPORT("transport", R.string.exp_transport, "🚌", Color(0xFF0EA5E9)),
    SHOPPING("shopping", R.string.exp_shopping, "🛍️", Color(0xFFDB2777)),
    FUN("fun", R.string.exp_fun, "🎉", Color(0xFF8B5CF6)),
    BILLS("bills", R.string.exp_bills, "🧾", Color(0xFF475569)),
    HEALTH("health", R.string.exp_health, "💊", Color(0xFF10B981)),
    OTHER("other", R.string.exp_other, "✨", Color(0xFFB6B3C6));

    companion object {
        // Eski kayıtlarda kategori serbest metindi ("Genel" vb.): bunlar "Diğer" sayılır
        fun fromKey(key: String?): ExpenseCategory = entries.firstOrNull { it.key == key } ?: OTHER
    }
}

enum class CoffeeKind(@StringRes val label: Int, val emoji: String) {
    TURKISH(R.string.coffee_turkish, "☕"),
    ESPRESSO(R.string.coffee_espresso, "☕"),
    FILTER(R.string.coffee_filter, "☕"),
    LATTE(R.string.coffee_latte, "🥛"),
    CAPPUCCINO(R.string.coffee_cappuccino, "☁️"),
    ICED(R.string.coffee_iced, "🧊")
}

enum class DrinkKind(@StringRes val label: Int, val emoji: String) {
    BEER(R.string.drink_beer, "🍺"),
    WINE(R.string.drink_wine, "🍷"),
    RAKI(R.string.drink_raki, "🥛"),
    COCKTAIL(R.string.drink_cocktail, "🍸"),
    WHISKY(R.string.drink_whisky, "🥃"),
    OTHER(R.string.drink_other, "🍹")
}

// Alkol miktarı eski kayıtlarda serbest metin ("2 bira", "1,5"); baştaki sayı kadeh sayısı kabul edilir
private val LeadingNumber = Regex("""\d+(?:[.,]\d+)?""")

fun parseAlcoholDrinks(units: String?): Double? =
    units?.let { LeadingNumber.find(it)?.value?.replace(',', '.')?.toDoubleOrNull() }
