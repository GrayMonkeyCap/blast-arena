package com.lifeledger.core.security.di

import com.lifeledger.core.database.di.DatabasePassphraseProvider
import com.lifeledger.core.security.crypto.DatabaseKeyManager
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindsDatabasePassphraseProvider(
        impl: DatabaseKeyManager,
    ): DatabasePassphraseProvider
}
