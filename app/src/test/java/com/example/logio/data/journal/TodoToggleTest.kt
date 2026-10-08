/*
 * TodoToggleTest: Yapılacaklar listesinde madde işaretleme kuralının (toggleTodoLine) birim testleri.
 *
 * Kartta bir maddeye dokununca yalnızca o satır "[ ]" ↔ "[x]" değişmeli, diğerleri olduğu gibi kalmalı.
 */
package com.example.logio.data.journal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoToggleTest {

    private val content = "[ ] Süt al\n[x] Spor\n[ ] Kitap oku"

    @Test
    fun `isaretsiz madde isaretlenir, digerleri degismez`() {
        assertEquals("[x] Süt al\n[x] Spor\n[ ] Kitap oku", toggleTodoLine(content, 0))
    }

    @Test
    fun `isaretli madde geri alinir`() {
        assertEquals("[ ] Süt al\n[ ] Spor\n[ ] Kitap oku", toggleTodoLine(content, 1))
    }

    @Test
    fun `olmayan satir icin null doner`() {
        assertNull(toggleTodoLine(content, 3))
        assertNull(toggleTodoLine(content, -1))
    }

    @Test
    fun `isaret olmayan satira dokunulmaz`() {
        assertEquals("Başlık\n[ ] Madde", toggleTodoLine("Başlık\n[ ] Madde", 0))
    }

    @Test
    fun `madde metnindeki koseli parantez bozulmaz`() {
        assertEquals("[x] Liste [ ] yaz", toggleTodoLine("[ ] Liste [ ] yaz", 0))
    }
}
