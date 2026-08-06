package com.lifeledger.sms.merchant

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wires [MerchantResolver] for the app.
 *
 * The [AliasLookup] binding here is [NoOpAliasLookup] — a deliberate placeholder. This
 * module lives in `sms`, which has no database dependency (see [MerchantResolver] KDoc for
 * why that boundary matters), so it cannot provide the persistence-backed lookup itself.
 * Whichever module wires the `core.database`-backed [AliasLookup] implementation must
 * replace this `@Provides` (e.g. by not installing [MerchantModule] and providing its own,
 * or by qualifying both bindings) rather than adding a second unqualified provider, or Hilt
 * will fail at compile time with a duplicate-binding error.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class MerchantModule {

    @Binds
    @Singleton
    abstract fun bindsMerchantResolver(impl: DefaultMerchantResolver): MerchantResolver

    companion object {
        @Provides
        @Singleton
        fun providesAliasLookup(): AliasLookup = NoOpAliasLookup
    }
}
