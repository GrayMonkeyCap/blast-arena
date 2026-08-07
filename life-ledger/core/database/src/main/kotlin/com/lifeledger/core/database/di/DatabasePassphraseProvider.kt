package com.lifeledger.core.database.di

/**
 * Supplies the SQLCipher passphrase for the Life Ledger database.
 *
 * Declared here, implemented in `:core:security`. The inversion is deliberate: the database
 * module must not know how a key is derived, wrapped or unwrapped, and the security module
 * must not know anything about Room. It also keeps the dependency direction sane —
 * `:core:security` depends on `:core:database`, never the other way round.
 *
 * Implementations must return a *fresh* array on every call. The caller zeroes it as soon
 * as SQLCipher has consumed it, so a cached array would be blanked out from under the next
 * caller.
 */
interface DatabasePassphraseProvider {
    fun passphrase(): ByteArray
}
