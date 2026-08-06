package com.lifeledger.core.security.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** IV plus ciphertext produced by [KeystoreManager.encrypt]; both are needed to decrypt. */
data class SealedBytes(val iv: ByteArray, val ciphertext: ByteArray)

/**
 * Thrown when the AndroidKeyStore has permanently invalidated Life Ledger's master key.
 *
 * This is the documented recovery path for [KeyPermanentlyInvalidatedException]: the Keystore
 * itself has already destroyed the hardware-backed key material, so there is nothing to
 * "recover" cryptographically — a fresh key is generated so future [KeystoreManager.encrypt]
 * calls keep working, but anything previously wrapped with the old key (in practice, only the
 * on-device SQLCipher passphrase — see [DatabaseKeyManager]) is unrecoverable. Callers must
 * treat this the same as "the local database is gone" and fall back to onboarding /
 * re-ingesting SMS from scratch; there is no way to recover the old plaintext without it.
 */
class KeystoreKeyInvalidatedException(alias: String, cause: Throwable) : Exception(
    "AndroidKeyStore alias '$alias' was permanently invalidated by the platform; a new key " +
        "has been generated, but data wrapped with the old key can no longer be decrypted.",
    cause,
)

/**
 * Owns Life Ledger's single AndroidKeyStore master key and performs AES-256-GCM
 * encrypt/decrypt against it.
 *
 * The key is generated with `setUserAuthenticationRequired(false)`. This looks like the wrong
 * default for a "security" module, but it is deliberate: the only secret this key ever wraps
 * is the on-device SQLCipher database passphrase (see [DatabaseKeyManager]), and that
 * passphrase must be readable by background `WorkManager` jobs (SMS ingestion, bill-due
 * checks, insight generation) that run with no foreground activity and no unlocked screen to
 * prompt for. Requiring user authentication on this key would make every background write
 * fail whenever the device is locked. App-level "lock" (see [com.lifeledger.core.security.lock.AppLockManager])
 * is a separate, UI-level gate that hides data on screen; it is intentionally not the same
 * mechanism as this key's authentication requirement.
 *
 * `setRandomizedEncryptionRequired(true)` (the AES/GCM default) ensures a fresh, random IV is
 * used for every [encrypt] call so the same plaintext never produces the same ciphertext
 * twice.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE_PROVIDER).apply {
        load(null)
    }

    /** Encrypts [plaintext] under the master key, generating a fresh random IV. */
    fun encrypt(plaintext: ByteArray): SealedBytes {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext)
        return SealedBytes(iv = cipher.iv, ciphertext = ciphertext)
    }

    /**
     * Decrypts [sealed] using the master key.
     *
     * @throws KeystoreKeyInvalidatedException if the Keystore has permanently invalidated the
     * key (see the class doc for why this can happen and what a caller should do about it).
     */
    fun decrypt(sealed: SealedBytes): ByteArray {
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, sealed.iv)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), spec)
            return cipher.doFinal(sealed.ciphertext)
        } catch (cause: KeyPermanentlyInvalidatedException) {
            // The invalidated entry is useless; drop it so the next getOrCreateKey() call
            // generates a fresh one instead of repeatedly failing against a dead key.
            keyStore.deleteEntry(KEY_ALIAS)
            throw KeystoreKeyInvalidatedException(KEY_ALIAS, cause)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return generateKey()
    }

    private fun generateKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE_PROVIDER,
        )
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            // See class doc: the DB passphrase this key wraps must be readable by background
            // ingestion workers even while the app itself is locked.
            .setUserAuthenticationRequired(false)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEY_STORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "life_ledger_master"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
