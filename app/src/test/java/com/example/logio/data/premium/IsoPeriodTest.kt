/*
 * IsoPeriodTest: Play'in deneme süresini ("P7D", "P1W") gün sayısına çevirmesini dener.
 */
package com.example.logio.data.premium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsoPeriodTest {

    @Test
    fun `gun ve hafta gun sayisina cevrilir`() {
        assertEquals(7, PremiumManager.isoPeriodToDays("P7D"))
        assertEquals(7, PremiumManager.isoPeriodToDays("P1W"))
        assertEquals(14, PremiumManager.isoPeriodToDays("P2W"))
    }

    @Test
    fun `ay ve yil yaklasik gun olarak hesaplanir`() {
        assertEquals(30, PremiumManager.isoPeriodToDays("P1M"))
        assertEquals(365, PremiumManager.isoPeriodToDays("P1Y"))
    }

    @Test
    fun `anlasilamayan ya da sifir sure null doner`() {
        assertNull(PremiumManager.isoPeriodToDays("7 days"))
        assertNull(PremiumManager.isoPeriodToDays("P0D"))
        assertNull(PremiumManager.isoPeriodToDays(""))
    }
}
