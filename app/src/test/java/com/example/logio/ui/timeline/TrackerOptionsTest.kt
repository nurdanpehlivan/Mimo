/*
 * TrackerOptionsTest: Alkol kaydındaki yazıdan kadeh sayısını çıkaran parseAlcoholDrinks'in birim testleri.
 */
package com.example.logio.ui.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackerOptionsTest {

    @Test
    fun `sade sayi okunur`() {
        val sonuc = parseAlcoholDrinks("2")
        assertEquals(2.0, sonuc!!, 0.001)
    }

    @Test
    fun `virgullu sayi okunur`() {
        val sonuc = parseAlcoholDrinks("1,5 bira")
        assertEquals(1.5, sonuc!!, 0.001)
    }

    @Test
    fun `noktali sayi okunur`() {
        val sonuc = parseAlcoholDrinks("3.5")
        assertEquals(3.5, sonuc!!, 0.001)
    }

    @Test
    fun `bos deger bos doner`() {
        val sonuc = parseAlcoholDrinks(null)
        assertNull(sonuc)
    }

    @Test
    fun `sayi yoksa bos doner`() {
        val sonuc = parseAlcoholDrinks("bira")
        assertNull(sonuc)
    }

    @Test
    fun `sayi yazinin sonunda olsa da okunur`() {
        // Fonksiyonun yorumu "baştaki sayı" diyor ama aslında yazıdaki İLK sayıyı buluyor, nerede olursa olsun
        val sonuc = parseAlcoholDrinks("bira 2")
        assertEquals(2.0, sonuc!!, 0.001)
    }
}
