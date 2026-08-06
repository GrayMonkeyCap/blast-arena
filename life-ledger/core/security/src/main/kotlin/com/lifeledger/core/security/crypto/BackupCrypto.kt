package com.lifeledger.core.security.crypto

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The backup file does not start with the expected header, is truncated, or is too new. */
class InvalidBackupHeaderException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/** The header was fine but the ciphertext failed to authenticate — wrong passphrase or corruption. */
class BackupDecryptionException(message: String, cause: Throwable? = null) :
    Exception(message, cause)

/**
 * Encrypts and decrypts Life Ledger backup files with a user-supplied passphrase.
 *
 * Threat model: this protects a backup file *after it leaves the device* — emailed,
 * uploaded to the user's own cloud storage, copied over USB. The on-device database is
 * protected separately, by [KeystoreManager]/[DatabaseKeyManager], with a key the user never
 * sees and that never leaves the device's secure hardware. A backup cannot use that
 * mechanism because it has to be decryptable on a different device (or a reinstall) using
 * only something the user remembers, which is why this is passphrase-based PBKDF2 + AES-GCM
 * rather than Keystore-based: a Keystore key is deliberately *not* exportable.
 *
 * Both directions stream through a fixed-size buffer rather than loading the backup into
 * memory, since a backup can be several hundred MB. Decryption deliberately does not use
 * `CipherInputStream`: historically, `CipherInputStream.read()` has swallowed authentication
 * failures on some JDKs instead of propagating them (a wrong passphrase would silently
 * produce garbage output instead of failing). Calling `Cipher.update`/`doFinal` directly
 * guarantees `doFinal()` throws [AEADBadTagException] when the GCM tag does not match.
 */
object BackupCrypto {

    private const val MAGIC = "LLBK"
    private const val FORMAT_VERSION: Int = 1

    /** OWASP-recommended minimum for PBKDF2-HMAC-SHA256 as of this writing. */
    private const val ITERATIONS = 210_000
    private const val SALT_LENGTH_BYTES = 16
    private const val IV_LENGTH_BYTES = 12
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val BUFFER_SIZE = 8 * 1024

    /** Encrypts everything read from [source] into [sink], writing the header first. */
    fun encrypt(source: InputStream, sink: OutputStream, passphrase: CharArray) {
        val salt = randomBytes(SALT_LENGTH_BYTES)
        val iv = randomBytes(IV_LENGTH_BYTES)
        val key = deriveKey(passphrase, salt, ITERATIONS)

        writeHeader(sink, Header(FORMAT_VERSION, ITERATIONS, salt, iv))

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        pump(source, sink, cipher)
        sink.flush()
    }

    /**
     * Decrypts a stream previously produced by [encrypt] from [source] into [sink].
     *
     * @throws InvalidBackupHeaderException if [source] is not a Life Ledger backup, is
     * truncated, or was written by an unsupported (newer) format version.
     * @throws BackupDecryptionException if the passphrase is wrong or the ciphertext was
     * corrupted or tampered with.
     */
    fun decrypt(source: InputStream, sink: OutputStream, passphrase: CharArray) {
        val header = readHeader(source)
        val key = deriveKey(passphrase, header.salt, header.iterations)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, header.iv))
        try {
            pump(source, sink, cipher)
            sink.flush()
        } catch (cause: AEADBadTagException) {
            throw BackupDecryptionException("Wrong passphrase or corrupted backup file", cause)
        } catch (cause: GeneralSecurityException) {
            throw BackupDecryptionException("Backup file could not be decrypted", cause)
        }
    }

    /** Runs every byte of [source] through [cipher], writing the result to [sink] as it goes. */
    private fun pump(source: InputStream, sink: OutputStream, cipher: Cipher) {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        while (source.read(buffer).also { bytesRead = it } != -1) {
            val chunk = cipher.update(buffer, 0, bytesRead)
            if (chunk != null && chunk.isNotEmpty()) sink.write(chunk)
        }
        val finalChunk = cipher.doFinal()
        if (finalChunk.isNotEmpty()) sink.write(finalChunk)
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, iterations, KEY_LENGTH_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun randomBytes(length: Int): ByteArray =
        ByteArray(length).also { SecureRandom().nextBytes(it) }

    private data class Header(
        val version: Int,
        val iterations: Int,
        val salt: ByteArray,
        val iv: ByteArray,
    )

    /**
     * Header layout, written in order: 4-byte ASCII magic `LLBK`; 1-byte format version;
     * 4-byte iteration count (so a future format bump can raise PBKDF2 cost without breaking
     * old backups); 1-byte salt length + salt; 1-byte IV length + IV. Everything after the
     * header is GCM ciphertext.
     */
    private fun writeHeader(sink: OutputStream, header: Header) {
        val out = DataOutputStream(sink)
        out.writeBytes(MAGIC)
        out.writeByte(header.version)
        out.writeInt(header.iterations)
        out.writeByte(header.salt.size)
        out.write(header.salt)
        out.writeByte(header.iv.size)
        out.write(header.iv)
        out.flush() // Must not close `out`: closing would close the underlying `sink`.
    }

    private fun readHeader(source: InputStream): Header {
        val input = DataInputStream(source)
        try {
            val magic = ByteArray(MAGIC.length)
            input.readFully(magic)
            if (String(magic, Charsets.US_ASCII) != MAGIC) {
                throw InvalidBackupHeaderException("Not a Life Ledger backup file")
            }

            val version = input.readUnsignedByte()
            if (version != FORMAT_VERSION) {
                throw InvalidBackupHeaderException(
                    "Unsupported backup format version $version (this app supports $FORMAT_VERSION)",
                )
            }

            val iterations = input.readInt()
            val salt = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
            val iv = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }

            return Header(version, iterations, salt, iv)
        } catch (cause: EOFException) {
            throw InvalidBackupHeaderException("Truncated backup file header", cause)
        }
    }
}
