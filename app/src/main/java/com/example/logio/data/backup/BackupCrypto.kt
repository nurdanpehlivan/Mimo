/*
 * BackupCrypto: Yedek dosyasını kullanıcının belirlediği şifreyle şifreler ve çözer.
 *
 * Anahtar şifreden PBKDF2 (HMAC-SHA256, 210.000 tur) ile türetilir; veri AES-256-GCM ile şifrelenir.
 * Büyük dosyalar (Journey videoları) belleğe bir anda alınmasın diye veri 1 MB'lık parçalar halinde işlenir.
 *
 * Dosya biçimi:
 *   "LOGIOBK1" (8 bayt) | tuz (16 bayt) | tur sayısı (4 bayt)
 *   ardından her parça için: son-parça işareti (1 bayt) | uzunluk (4 bayt) | IV (12 bayt) | şifreli veri + etiket
 * Her parçanın sıra numarası ve "son parça" işareti doğrulanan ek veriye (AAD) girer: parçaların yeri
 * değiştirilirse, biri silinirse ya da dosya yarıda kesilirse çözme başarısız olur.
 */
package com.example.logio.data.backup

import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Şifre yanlış (ya da dosyanın başı bozuk). */
class WrongPasswordException : IOException("Yedek şifresi yanlış")

/** Dosya bir Logio yedeği değil. */
class NotABackupException : IOException("Logio yedeği değil")

internal object BackupCrypto {
    private val MAGIC = "LOGIOBK1".toByteArray(Charsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128
    private const val TAG_SIZE = TAG_BITS / 8
    internal const val CHUNK_SIZE = 1 shl 20
    internal const val DEFAULT_ITERATIONS = 210_000
    private const val MAX_ITERATIONS = 10_000_000
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val random = SecureRandom()

    internal fun randomBytes(size: Int) = ByteArray(size).also { random.nextBytes(it) }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int): SecretKey {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            return SecretKeySpec(bytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    /** Parça sırası ve son-parça işareti: şifreli veriyle birlikte doğrulanır. */
    private fun aad(index: Long, last: Boolean): ByteArray =
        ByteBuffer.allocate(9).putLong(index).put(if (last) 1 else 0).array()

    /** [out]'a yazılanları şifreler. Kapatılınca son parça yazılır; kapatılmazsa dosya geçersiz kalır. */
    fun encryptingStream(out: OutputStream, password: CharArray, iterations: Int = DEFAULT_ITERATIONS): OutputStream {
        val salt = randomBytes(SALT_SIZE)
        val key = deriveKey(password, salt, iterations)
        out.write(MAGIC)
        out.write(salt)
        out.write(ByteBuffer.allocate(4).putInt(iterations).array())
        return EncryptingOutputStream(out, key)
    }

    /** [input]'taki yedeği çözerek okur. Şifre yanlışsa ilk okumada [WrongPasswordException] fırlatır. */
    fun decryptingStream(input: InputStream, password: CharArray): InputStream {
        val data = DataInputStream(input)
        val header = ByteArray(MAGIC.size)
        try {
            data.readFully(header)
        } catch (e: EOFException) {
            throw NotABackupException()
        }
        if (!header.contentEquals(MAGIC)) throw NotABackupException()
        val salt = ByteArray(SALT_SIZE).also { data.readFully(it) }
        val iterations = data.readInt()
        if (iterations !in 1..MAX_ITERATIONS) throw NotABackupException()
        return DecryptingInputStream(data, deriveKey(password, salt, iterations))
    }

    private class EncryptingOutputStream(private val out: OutputStream, private val key: SecretKey) : OutputStream() {
        private val buffer = ByteArray(CHUNK_SIZE)
        private var size = 0
        private var index = 0L
        private var closed = false

        override fun write(b: Int) {
            if (size == buffer.size) writeChunk(last = false)
            buffer[size++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var offset = off
            var remaining = len
            while (remaining > 0) {
                if (size == buffer.size) writeChunk(last = false)
                val n = minOf(remaining, buffer.size - size)
                System.arraycopy(b, offset, buffer, size, n)
                size += n
                offset += n
                remaining -= n
            }
        }

        private fun writeChunk(last: Boolean) {
            val iv = randomBytes(IV_SIZE)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(index, last))
            val encrypted = cipher.doFinal(buffer, 0, size)
            out.write(if (last) 1 else 0)
            out.write(ByteBuffer.allocate(4).putInt(encrypted.size).array())
            out.write(iv)
            out.write(encrypted)
            index++
            size = 0
        }

        // Yarım parça yazılamaz; veri yalnızca parça dolunca ya da kapatılınca yazılır
        override fun flush() = Unit

        override fun close() {
            if (closed) return
            closed = true
            try {
                writeChunk(last = true)
                out.flush()
            } finally {
                out.close()
            }
        }
    }

    private class DecryptingInputStream(private val input: DataInputStream, private val key: SecretKey) : InputStream() {
        private var chunk = ByteArray(0)
        private var position = 0
        private var index = 0L
        private var finished = false

        /** Bir sonraki parçayı çözer; son parça daha önce okunduysa false döner. */
        private fun nextChunk(): Boolean {
            if (finished) return false
            val last = try {
                when (input.readUnsignedByte()) {
                    0 -> false
                    1 -> true
                    else -> throw IOException("Yedek dosyası bozuk")
                }
            } catch (e: EOFException) {
                throw IOException("Yedek dosyası eksik (yarıda kesilmiş)")
            }
            val length = input.readInt()
            if (length < TAG_SIZE || length > CHUNK_SIZE + TAG_SIZE) throw IOException("Yedek dosyası bozuk")
            val iv = ByteArray(IV_SIZE).also { input.readFully(it) }
            val encrypted = ByteArray(length).also { input.readFully(it) }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            cipher.updateAAD(aad(index, last))
            chunk = try {
                cipher.doFinal(encrypted)
            } catch (e: AEADBadTagException) {
                // İlk parça açılamıyorsa şifre yanlıştır; sonrakiler açılamıyorsa dosya bozulmuştur
                throw if (index == 0L) WrongPasswordException() else IOException("Yedek dosyası bozuk", e)
            }
            position = 0
            index++
            finished = last
            return true
        }

        override fun read(): Int {
            while (position >= chunk.size) {
                if (!nextChunk()) return -1
            }
            return chunk[position++].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (position >= chunk.size) {
                if (!nextChunk()) return -1
            }
            val n = minOf(len, chunk.size - position)
            System.arraycopy(chunk, position, b, off, n)
            position += n
            return n
        }

        /** Dosyanın sonuna kadar okunup son parçanın doğrulandığından emin olur (kesilmiş dosyayı yakalar). */
        fun verifyToEnd() {
            val skip = ByteArray(64 * 1024)
            while (read(skip, 0, skip.size) != -1) Unit
            if (!finished) throw IOException("Yedek dosyası eksik (yarıda kesilmiş)")
        }

        override fun close() = input.close()
    }

    /** Okuma bitince çağrılır: [stream] bu sınıfın çözme akışıysa dosyanın tamamını doğrular. */
    fun verifyToEnd(stream: InputStream) {
        (stream as? DecryptingInputStream)?.verifyToEnd()
    }
}
