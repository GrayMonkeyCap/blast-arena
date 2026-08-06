package com.lifeledger.core.security.crypto

import android.content.Context
import com.lifeledger.core.database.di.DatabasePassphraseProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates, wraps and reveals the SQLCipher passphrase used by `:core:database`.
 *
 * The plaintext passphrase never touches disk. What is persisted, under
 * `context.filesDir/.dbkey`, is the *wrapped* form: an AES-GCM IV plus ciphertext produced by
 * [KeystoreManager]. A plain `java.io.File` — not `androidx.security.crypto.EncryptedFile` —
 * is the right tool here: the bytes on disk are already ciphertext whose decryption key lives
 * only inside the device's Keystore (TEE/StrongBox) and never leaves it, so wrapping the file
 * *again* with `EncryptedFile` would add a second layer of encryption whose key is protected
 * by exactly the same hardware, for no additional security — it would only add another
 * moving part that itself needs a Keystore-backed key to unwrap.
 */
@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keystoreManager: KeystoreManager,
) : DatabasePassphraseProvider {

    private val keyFile: File get() = File(context.filesDir, WRAPPED_KEY_FILE_NAME)

    /**
     * Returns the database passphrase, decrypting it fresh on every call rather than caching
     * it in memory, so the plaintext key's lifetime in the process' heap is as short as
     * possible. On first run this also generates the passphrase and persists its wrapped
     * form. Callers must call [zeroize] on the returned array as soon as they are done with
     * it (SQLCipher itself copies the bytes it needs, so the array is safe to wipe right
     * after the database is opened).
     */
    override fun passphrase(): ByteArray = synchronized(lock) {
        val sealed = readWrapped() ?: createAndPersistPassphrase()
        keystoreManager.decrypt(sealed)
    }

    /** Overwrites [bytes] in place so the passphrase does not linger in the heap or a GC copy. */
    fun zeroize(bytes: ByteArray) {
        bytes.fill(0)
    }

    private fun createAndPersistPassphrase(): SealedBytes {
        val raw = ByteArray(PASSPHRASE_LENGTH_BYTES)
        SecureRandom().nextBytes(raw)
        return try {
            val sealed = keystoreManager.encrypt(raw)
            writeWrapped(sealed)
            sealed
        } finally {
            zeroize(raw)
        }
    }

    /**
     * Reads the wrapped passphrase file, a tiny hand-rolled format: a 4-byte big-endian IV
     * length, the IV, and the remaining bytes as ciphertext. Returns `null` when the file does
     * not exist yet (first run) or is too short to contain a valid header.
     */
    private fun readWrapped(): SealedBytes? {
        if (!keyFile.exists()) return null
        return RandomAccessFile(keyFile, "r").use { file ->
            try {
                val ivLength = file.readInt()
                val iv = ByteArray(ivLength)
                file.readFully(iv)
                val ciphertext = ByteArray((file.length() - file.filePointer).toInt())
                file.readFully(ciphertext)
                SealedBytes(iv, ciphertext)
            } catch (_: EOFException) {
                null
            }
        }
    }

    private fun writeWrapped(sealed: SealedBytes) {
        keyFile.parentFile?.mkdirs()
        RandomAccessFile(keyFile, "rw").use { file ->
            file.setLength(0)
            file.writeInt(sealed.iv.size)
            file.write(sealed.iv)
            file.write(sealed.ciphertext)
        }
    }

    companion object {
        private const val WRAPPED_KEY_FILE_NAME = ".dbkey"

        /** 256-bit passphrase, matching the AES-256 key size SQLCipher derives from it. */
        private const val PASSPHRASE_LENGTH_BYTES = 32

        private val lock = Any()
    }
}
