package com.lifeledger.domain.di

import com.lifeledger.domain.insight.DefaultInsightEngine
import com.lifeledger.domain.insight.InsightEngine
import com.lifeledger.domain.insight.InsightGenerator
import com.lifeledger.domain.insight.generators.BillChangeGenerator
import com.lifeledger.domain.insight.generators.IncomePatternGenerator
import com.lifeledger.domain.insight.generators.InvestmentStreakGenerator
import com.lifeledger.domain.insight.generators.LargeTransactionGenerator
import com.lifeledger.domain.insight.generators.SavingsRateGenerator
import com.lifeledger.domain.insight.generators.SpendTrendGenerator
import com.lifeledger.domain.insight.generators.SubscriptionInsightGenerator
import com.lifeledger.domain.nlq.AskService
import com.lifeledger.domain.nlq.DefaultAskService
import com.lifeledger.domain.nlq.DefaultQueryAnswerer
import com.lifeledger.domain.nlq.QueryAnswerer
import com.lifeledger.domain.nlq.QueryInterpreter
import com.lifeledger.domain.nlq.RuleBasedQueryInterpreter
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Wiring for the domain layer.
 *
 * Insight generators are contributed as a multibinding set: adding an insight means adding
 * a class and one `@Binds @IntoSet` line, and nothing that consumes insights changes. The
 * same shape is used for parsers in `:sms`, for the same reason.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindsInsightEngine(impl: DefaultInsightEngine): InsightEngine

    /**
     * The shipped interpreter. Swapping in an on-device model later means changing this one
     * binding — see [com.lifeledger.domain.nlq.LocalLanguageModel].
     */
    @Binds
    @Singleton
    abstract fun bindsQueryInterpreter(impl: RuleBasedQueryInterpreter): QueryInterpreter

    @Binds
    @Singleton
    abstract fun bindsQueryAnswerer(impl: DefaultQueryAnswerer): QueryAnswerer

    @Binds
    @Singleton
    abstract fun bindsAskService(impl: DefaultAskService): AskService

    @Binds
    @IntoSet
    abstract fun bindsSpendTrend(impl: SpendTrendGenerator): InsightGenerator

    @Binds
    @IntoSet
    abstract fun bindsSubscriptions(impl: SubscriptionInsightGenerator): InsightGenerator

    @Binds
    @IntoSet
    abstract fun bindsInvestmentStreak(impl: InvestmentStreakGenerator): InsightGenerator

    @Binds
    @IntoSet
    abstract fun bindsBillChange(impl: BillChangeGenerator): InsightGenerator

    @Binds
    @IntoSet
    abstract fun bindsIncomePattern(impl: IncomePatternGenerator): InsightGenerator

    @Binds
    @IntoSet
    abstract fun bindsSavingsRate(impl: SavingsRateGenerator): InsightGenerator

    @Binds
    @IntoSet
    abstract fun bindsLargeTransaction(impl: LargeTransactionGenerator): InsightGenerator
}
