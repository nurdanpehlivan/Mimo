/*
 * TemplateTextTest: Kayıt metinlerinin bir dilin şablonundan çıkarılıp başka dilin şablonuna yerleştirilmesini dener.
 */
package com.example.logio.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class TemplateTextTest {

    @Test
    fun `su metni Turkceden Ingilizceye cevrilir`() {
        val match = TemplateText.match("250 ml su içildi.", "%d ml su içildi.", keepSuffix = false)!!
        assertEquals(listOf("250"), match.args)
        assertEquals("Drank 250 ml of water.", TemplateText.fill("Drank %d ml of water.", match.args, Locale.ENGLISH))
    }

    @Test
    fun `kullanicinin kendi yazdigi metin sablona uymaz`() {
        assertNull(TemplateText.match("Bugün çok su içtim", "%d ml su içildi.", keepSuffix = false))
    }

    @Test
    fun `numarali yer tutucular dogru sirayla okunur`() {
        val match = TemplateText.match(
            "Health: 7.5 hours of sleep (23:10 – 06:40).",
            "Health: %1\$s hours of sleep (%2\$s – %3\$s).",
            keepSuffix = false
        )!!
        assertEquals(listOf("7.5", "23:10", "06:40"), match.args)
        assertEquals(
            "Health Connect: 7.5 Stunden Schlaf (23:10 – 06:40).",
            TemplateText.fill("Health Connect: %1\$s Stunden Schlaf (%2\$s – %3\$s).", match.args, Locale.GERMAN)
        )
    }

    @Test
    fun `sondaki not korunur`() {
        val match = TemplateText.match("8.0 hours of sleep (manual). Erken yattım", "%s hours of sleep (manual).", keepSuffix = true)!!
        assertEquals(listOf("8.0"), match.args)
        assertEquals(" Erken yattım", match.suffix)
    }

    @Test
    fun `cok satirli sablon eslesir`() {
        val format = "Artist: %1\$s\nNote: %2\$s"
        val match = TemplateText.match("Artist: Sezen Aksu\nNote: favorim", format, keepSuffix = false)!!
        assertEquals(listOf("Sezen Aksu", "favorim"), match.args)
    }

    @Test
    fun `regex ozel karakterleri duz metin sayilir`() {
        // Şablondaki nokta ve parantez "herhangi bir karakter" gibi davranmamalı
        assertNull(TemplateText.match("8.0 hours of sleep Xmanual)!", "%s hours of sleep (manual).", keepSuffix = false))
    }
}
