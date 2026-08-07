package com.lifeledger.core.security.crypto

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.Test

class BackupCryptoTest {

    private val passphrase = "correct horse battery staple".toCharArray()
    private val wrongPassphrase = "wrong passphrase entirely".toCharArray()

    @Test
    fun `decrypt recovers exactly what was encrypted`() {
        val plaintext = "Life Ledger backup payload — transactions, merchants, accounts.".toByteArray()

        val encrypted = encryptToBytes(plaintext, passphrase)
        val decrypted = decryptToBytes(encrypted, passphrase)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `round-trips a large multi-chunk payload`() {
        // Larger than BackupCrypto's internal buffer so the streaming loop runs many times.
        val plaintext = Random(seed = 42).nextBytes(500_000)

        val encrypted = encryptToBytes(plaintext, passphrase)
        val decrypted = decryptToBytes(encrypted, passphrase)

        assertThat(decrypted).isEqualTo(plaintext)
    }

    @Test
    fun `decrypt with the wrong passphrase throws BackupDecryptionException`() {
        val plaintext = "sensitive backup contents".toByteArray()
        val encrypted = encryptToBytes(plaintext, passphrase)

        assertThrows(BackupDecryptionException::class.java) {
            decryptToBytes(encrypted, wrongPassphrase)
        }
    }

    @Test
    fun `decrypt rejects a tampered ciphertext`() {
        val plaintext = "sensitive backup contents".toByteArray()
        val encrypted = encryptToBytes(plaintext, passphrase)
        // Flip a byte well past the header, inside the ciphertext.
        encrypted[encrypted.size - 1] = (encrypted[encrypted.size - 1] + 1).toByte()

        assertThrows(BackupDecryptionException::class.java) {
            decryptToBytes(encrypted, passphrase)
        }
    }

    @Test
    fun `decrypt rejects an unsupported format version`() {
        val plaintext = "sensitive backup contents".toByteArray()
        val encrypted = encryptToBytes(plaintext, passphrase)
        // Byte 4 (0-indexed) is the version byte, right after the 4-byte "LLBK" magic.
        encrypted[4] = 99

        assertThrows(InvalidBackupHeaderException::class.java) {
            decryptToBytes(encrypted, passphrase)
        }
    }

    @Test
    fun `decrypt rejects a file with the wrong magic`() {
        val plaintext = "sensitive backup contents".toByteArray()
        val encrypted = encryptToBytes(plaintext, passphrase)
        encrypted[0] = 'X'.code.toByte()

        assertThrows(InvalidBackupHeaderException::class.java) {
            decryptToBytes(encrypted, passphrase)
        }
    }

    @Test
    fun `decrypt rejects a truncated header`() {
        val plaintext = "sensitive backup contents".toByteArray()
        val encrypted = encryptToBytes(plaintext, passphrase)
        // Cut the stream off inside the header, well before any ciphertext.
        val truncated = encrypted.copyOfRange(0, 3)

        assertThrows(InvalidBackupHeaderException::class.java) {
            decryptToBytes(truncated, passphrase)
        }
    }

    @Test
    fun `decrypt rejects an empty file`() {
        assertThrows(InvalidBackupHeaderException::class.java) {
            decryptToBytes(ByteArray(0), passphrase)
        }
    }

    private fun encryptToBytes(plaintext: ByteArray, passphrase: CharArray): ByteArray {
        val output = ByteArrayOutputStream()
        BackupCrypto.encrypt(ByteArrayInputStream(plaintext), output, passphrase.copyOf())
        return output.toByteArray()
    }

    private fun decryptToBytes(ciphertext: ByteArray, passphrase: CharArray): ByteArray {
        val output = ByteArrayOutputStream()
        BackupCrypto.decrypt(ByteArrayInputStream(ciphertext), output, passphrase.copyOf())
        return output.toByteArray()
    }

    private fun assertThrows(expected: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            assertThat(t).isInstanceOf(expected)
            return
        }
        throw AssertionError("Expected ${expected.simpleName} but nothing was thrown")
    }
}
