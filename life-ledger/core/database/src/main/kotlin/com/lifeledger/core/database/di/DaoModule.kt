package com.lifeledger.core.database.di

import com.lifeledger.core.database.LifeLedgerDatabase
import com.lifeledger.core.database.dao.AccountDao
import com.lifeledger.core.database.dao.BillDao
import com.lifeledger.core.database.dao.InsightDao
import com.lifeledger.core.database.dao.InvestmentDao
import com.lifeledger.core.database.dao.InvestmentTransactionDao
import com.lifeledger.core.database.dao.MerchantAliasDao
import com.lifeledger.core.database.dao.MerchantDao
import com.lifeledger.core.database.dao.ParseLogDao
import com.lifeledger.core.database.dao.ParserStateDao
import com.lifeledger.core.database.dao.SmsDao
import com.lifeledger.core.database.dao.SubscriptionDao
import com.lifeledger.core.database.dao.TagDao
import com.lifeledger.core.database.dao.TimelineEventDao
import com.lifeledger.core.database.dao.TransactionDao
import com.lifeledger.core.database.dao.UserRuleDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Exposes each DAO as its own binding.
 *
 * Repositories take the one or two DAOs they actually use rather than the whole database.
 * That keeps their constructors honest about what they touch and makes them trivial to
 * instantiate in a test with a single in-memory DAO.
 */
@Module
@InstallIn(SingletonComponent::class)
object DaoModule {

    @Provides
    fun providesSmsDao(database: LifeLedgerDatabase): SmsDao = database.smsDao()

    @Provides
    fun providesTransactionDao(database: LifeLedgerDatabase): TransactionDao =
        database.transactionDao()

    @Provides
    fun providesMerchantDao(database: LifeLedgerDatabase): MerchantDao = database.merchantDao()

    @Provides
    fun providesMerchantAliasDao(database: LifeLedgerDatabase): MerchantAliasDao =
        database.merchantAliasDao()

    @Provides
    fun providesAccountDao(database: LifeLedgerDatabase): AccountDao = database.accountDao()

    @Provides
    fun providesInvestmentDao(database: LifeLedgerDatabase): InvestmentDao =
        database.investmentDao()

    @Provides
    fun providesInvestmentTransactionDao(database: LifeLedgerDatabase): InvestmentTransactionDao =
        database.investmentTransactionDao()

    @Provides
    fun providesSubscriptionDao(database: LifeLedgerDatabase): SubscriptionDao =
        database.subscriptionDao()

    @Provides
    fun providesBillDao(database: LifeLedgerDatabase): BillDao = database.billDao()

    @Provides
    fun providesTagDao(database: LifeLedgerDatabase): TagDao = database.tagDao()

    @Provides
    fun providesTimelineEventDao(database: LifeLedgerDatabase): TimelineEventDao =
        database.timelineEventDao()

    @Provides
    fun providesUserRuleDao(database: LifeLedgerDatabase): UserRuleDao = database.userRuleDao()

    @Provides
    fun providesParseLogDao(database: LifeLedgerDatabase): ParseLogDao = database.parseLogDao()

    @Provides
    fun providesInsightDao(database: LifeLedgerDatabase): InsightDao = database.insightDao()

    @Provides
    fun providesParserStateDao(database: LifeLedgerDatabase): ParserStateDao =
        database.parserStateDao()
}
