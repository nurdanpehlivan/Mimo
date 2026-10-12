/*
 * BackupCryptoTest: Yedek şifrelemesinin (BackupCrypto.kt) birim testleri.
 *
 * Doğru şifreyle veri birebir geri gelmeli; yanlış şifre, bozulmuş ya da yarıda kesilmiş dosya
 * ve Logio yedeği olmayan dosya mutlaka fark edilmeli.
 */
package com.example.logio.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class BackupCryptoTest {

    // Testler hızlı çalışsın diye tur sayısı düşük; uygulama 210.000 tur kullanır
    private val iterations = 1_000
    private val password = "doğru-şifre"

    private fun encrypt(data: ByteArray, pass: String = password): ByteArray {
        val out = ByteArrayOutputStream()
        BackupCrypto.encryptingStream(out, pass.toCharArray(), iterations).use { it.write(data) }
        return out.toByteArray()
    }

    private fun decrypt(file: ByteArray, pass: String = password): ByteArray {
        val stream = BackupCrypto.decryptingStream(ByteArrayInputStream(file), pass.toCharArray())
        val result = stream.readBytes()
        BackupCrypto.verifyToEnd(stream)
        return result
    }

    @Test
    fun `dogru sifreyle veri birebir geri gelir`() {
        val data = "Bugün 2 fincan Latte ☕".toByteArray()
        assertArrayEquals(data, decrypt(encrypt(data)))
    }

    @Test
    fun `birden cok parcali buyuk veri dogru birlesir`() {
        // 2,5 MB: üç parça (1 MB + 1 MB + 0,5 MB)
        val data = ByteArray(BackupCrypto.CHUNK_SIZE * 5 / 2) { (it % 251).toByte() }
        assertArrayEquals(data, decrypt(encrypt(data)))
    }

    @Test
    fun `bos veri de sifrelenip acilabilir`() {
        assertArrayEquals(ByteArray(0), decrypt(encrypt(ByteArray(0))))
    }

    @Test
    fun `ayni veri iki kez sifrelenince farkli dosya cikar`() {
        val data = "aynı içerik".toByteArray()
        assertTrue("Rastgele tuz ve IV kullanılmalı", !encrypt(data).contentEquals(encrypt(data)))
    }

    @Test(expected = WrongPasswordException::class)
    fun `yanlis sifre fark edilir`() {
        decrypt(encrypt("gizli".toByteArray()), pass = "yanlış-şifre")
    }

    @Test(expected = NotABackupException::class)
    fun `logio yedegi olmayan dosya reddedilir`() {
        decrypt("bu bir fotoğraf dosyası".toByteArray())
    }

    @Test
    fun `degistirilmis dosya fark edilir`() {
        val file = encrypt(ByteArray(BackupCrypto.CHUNK_SIZE * 2) { 7 })
        file[file.size - 100] = (file[file.size - 100].toInt() xor 1).toByte()
        expectIOException { decrypt(file) }
    }

    @Test
    fun `yarida kesilmis dosya fark edilir`() {
        // İlk parçanın sonunda kesilmiş: ilk parça doğru açılır ama son parça hiç gelmez
        val file = encrypt(ByteArray(BackupCrypto.CHUNK_SIZE * 2) { 3 })
        val headerSize = 8 + 16 + 4
        val firstChunkSize = 1 + 4 + 12 + BackupCrypto.CHUNK_SIZE + 16
        expectIOException { decrypt(file.copyOf(headerSize + firstChunkSize)) }
    }

    private fun expectIOException(block: () -> Unit) {
        try {
            block()
            fail("Hata bekleniyordu")
        } catch (e: IOException) {
            // beklenen
        }
    }
}
