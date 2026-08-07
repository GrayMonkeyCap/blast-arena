package com.lifeledger.core.database.di

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.lifeledger.core.database.LifeLedgerDatabase
import com.lifeledger.core.database.migration.LifeLedgerMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Builds the encrypted database.
 *
 * Two decisions here are load-bearing for the product's privacy claim:
 *
 *  - The open-helper factory is SQLCipher's, so the file on disk is ciphertext. An attacker
 *    with the raw `.db` file — from a filesystem dump, a rooted device, or a stolen
 *    unencrypted backup — gets nothing without the Keystore-held key.
 *  - The passphrase byte array is zeroed the moment SQLCipher has taken it, so the key does
 *    not sit in the heap for the life of the process waiting to be scraped.
 *
 * `fallbackToDestructiveMigration` is deliberately absent. This database is the user's only
 * copy of years of reconstructed history; losing it to a schema bump would be the single
 * worst thing the app could do.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providesOpenHelperFactory(
        passphraseProvider: DatabasePassphraseProvider,
    ): SupportSQLiteOpenHelper.Factory {
        System.loadLibrary(SQLCIPHER_LIBRARY)
        val passphrase = passphraseProvider.passphrase()
        return try {
            SupportOpenHelperFactory(passphrase)
        } finally {
            passphrase.fill(0)
        }
    }

    @Provides
    @Singleton
    fun providesDatabase(
        @ApplicationContext context: Context,
        openHelperFactory: SupportSQLiteOpenHelper.Factory,
    ): LifeLedgerDatabase = Room
        .databaseBuilder(context, LifeLedgerDatabase::class.java, LifeLedgerDatabase.DATABASE_NAME)
        .openHelperFactory(openHelperFactory)
        .addMigrations(*LifeLedgerMigrations.ALL)
        .addCallback(
            object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    // Foreign keys are off by default in SQLite and Room only enables them
                    // for its own generated code paths; the pipeline uses raw queries too.
                    db.execSQL("PRAGMA foreign_keys = ON")
                }
            },
        )
        .build()

    private const val SQLCIPHER_LIBRARY = "sqlcipher"
}
