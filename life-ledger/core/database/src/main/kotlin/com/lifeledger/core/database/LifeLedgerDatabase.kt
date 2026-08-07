package com.lifeledger.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.lifeledger.core.database.converter.Converters
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
import com.lifeledger.core.database.entity.AccountEntity
import com.lifeledger.core.database.entity.BillEntity
import com.lifeledger.core.database.entity.InsightEntity
import com.lifeledger.core.database.entity.InvestmentEntity
import com.lifeledger.core.database.entity.InvestmentTransactionEntity
import com.lifeledger.core.database.entity.MerchantAliasEntity
import com.lifeledger.core.database.entity.MerchantEntity
import com.lifeledger.core.database.entity.ParseLogEntity
import com.lifeledger.core.database.entity.ParserStateEntity
import com.lifeledger.core.database.entity.SmsEntity
import com.lifeledger.core.database.entity.SubscriptionEntity
import com.lifeledger.core.database.entity.TagEntity
import com.lifeledger.core.database.entity.TimelineEventEntity
import com.lifeledger.core.database.entity.TransactionEntity
import com.lifeledger.core.database.entity.TransactionFtsEntity
import com.lifeledger.core.database.entity.TransactionTagCrossRef
import com.lifeledger.core.database.entity.UserRuleEntity

/**
 * The one database. Encrypted at rest with SQLCipher; see `di.DatabaseModule`.
 *
 * There is deliberately no second database and no unencrypted "cache" one: everything Life
 * Ledger holds is derived from the user's messages, so a plaintext side table would just be
 * the same private data with the protection removed.
 *
 * `exportSchema` is on and `schemas/` is committed — see `migration.LifeLedgerMigrations` for
 * what that obliges every version bump to do.
 */
@Database(
    entities = [
        SmsEntity::class,
        TransactionEntity::class,
        TransactionFtsEntity::class,
        MerchantEntity::class,
        MerchantAliasEntity::class,
        AccountEntity::class,
        InvestmentEntity::class,
        InvestmentTransactionEntity::class,
        SubscriptionEntity::class,
        BillEntity::class,
        TagEntity::class,
        TransactionTagCrossRef::class,
        TimelineEventEntity::class,
        UserRuleEntity::class,
        ParseLogEntity::class,
        InsightEntity::class,
        ParserStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class LifeLedgerDatabase : RoomDatabase() {

    abstract fun smsDao(): SmsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun merchantDao(): MerchantDao
    abstract fun merchantAliasDao(): MerchantAliasDao
    abstract fun accountDao(): AccountDao
    abstract fun investmentDao(): InvestmentDao
    abstract fun investmentTransactionDao(): InvestmentTransactionDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun billDao(): BillDao
    abstract fun tagDao(): TagDao
    abstract fun timelineEventDao(): TimelineEventDao
    abstract fun userRuleDao(): UserRuleDao
    abstract fun parseLogDao(): ParseLogDao
    abstract fun insightDao(): InsightDao
    abstract fun parserStateDao(): ParserStateDao

    companion object {
        const val DATABASE_NAME = "life_ledger.db"
    }
}
