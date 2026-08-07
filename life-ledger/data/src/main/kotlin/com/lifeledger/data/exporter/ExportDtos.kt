package com.lifeledger.data.exporter

import com.lifeledger.core.model.Account
import com.lifeledger.core.model.AccountType
import com.lifeledger.core.model.Bill
import com.lifeledger.core.model.BillType
import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.InstrumentType
import com.lifeledger.core.model.Investment
import com.lifeledger.core.model.Merchant
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.Recurrence
import com.lifeledger.core.model.SourceKind
import com.lifeledger.core.model.Subscription
import com.lifeledger.core.model.Tag
import com.lifeledger.core.model.Transaction
import com.lifeledger.core.model.TransactionType
import com.lifeledger.core.model.TxnCategory
import com.lifeledger.core.model.UserRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Wire shapes for Life Ledger's own export format.
 *
 * These deliberately mirror the domain models field-for-field (enums as their `name`, [Money]
 * split into minor units + currency, [Instant]/[LocalDate] as ISO-8601 strings) rather than
 * reusing the domain classes directly with `kotlinx.serialization`: the domain models are free
 * to gain fields or change shape for UI/DB reasons without silently breaking the on-disk backup
 * format, and `ignoreUnknownKeys` on the read side means an older backup still imports cleanly
 * into a newer app version.
 */
@Serializable
data class ExportEnvelopeDto(
    val format: String = FORMAT_ID,
    val version: Int = FORMAT_VERSION,
    val exportedAt: String,
    val counts: ExportCountsDto = ExportCountsDto(),
    val transactions: List<TransactionDto> = emptyList(),
    val merchants: List<MerchantDto> = emptyList(),
    val accounts: List<AccountDto> = emptyList(),
    val subscriptions: List<SubscriptionDto> = emptyList(),
    val bills: List<BillDto> = emptyList(),
    val investments: List<InvestmentDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val rules: List<UserRuleDto> = emptyList(),
) {
    companion object {
        const val FORMAT_ID = "life-ledger-export"
        const val FORMAT_VERSION = 1
    }
}

@Serializable
data class ExportCountsDto(
    val transactions: Int = 0,
    val merchants: Int = 0,
    val accounts: Int = 0,
    val subscriptions: Int = 0,
    val bills: Int = 0,
    val investments: Int = 0,
    val tags: Int = 0,
    val rules: Int = 0,
)

@Serializable
data class TransactionDto(
    val id: Long = 0,
    val amountMinor: Long,
    val currency: String = Money.INR,
    val type: String,
    val direction: String,
    val category: String = TxnCategory.UNCATEGORIZED.name,
    val subcategory: String? = null,
    val paymentMethod: String = PaymentMethod.UNKNOWN.name,
    val occurredAt: String,
    val merchantId: Long? = null,
    val merchantName: String? = null,
    val rawMerchant: String? = null,
    val merchantConfidence: Float = 0f,
    val accountId: Long? = null,
    val maskedAccount: String? = null,
    val bankCode: String? = null,
    val upiId: String? = null,
    val balanceAfterMinor: Long? = null,
    val referenceNumber: String? = null,
    val transactionId: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val smsId: Long? = null,
    val parserId: String? = null,
    val parserConfidence: Float = 0f,
    val source: String = SourceKind.IMPORT.name,
    val userVerified: Boolean = false,
    val excludedFromStats: Boolean = false,
    val duplicateOfId: Long? = null,
    val tagIds: List<Long> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

fun Transaction.toDto(): TransactionDto = TransactionDto(
    id = id,
    amountMinor = amount.minor,
    currency = amount.currency,
    type = type.name,
    direction = direction.name,
    category = category.name,
    subcategory = subcategory,
    paymentMethod = paymentMethod.name,
    occurredAt = occurredAt.toString(),
    merchantId = merchantId,
    merchantName = merchantName,
    rawMerchant = rawMerchant,
    merchantConfidence = merchantConfidence.value,
    accountId = accountId,
    maskedAccount = maskedAccount,
    bankCode = bankCode,
    upiId = upiId,
    balanceAfterMinor = balanceAfter?.minor,
    referenceNumber = referenceNumber,
    transactionId = transactionId,
    description = description,
    notes = notes,
    smsId = smsId,
    parserId = parserId,
    parserConfidence = parserConfidence.value,
    source = source.name,
    userVerified = userVerified,
    excludedFromStats = excludedFromStats,
    duplicateOfId = duplicateOfId,
    tagIds = tagIds,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt.toString(),
)

fun TransactionDto.toDomain(): Transaction = Transaction(
    id = id,
    amount = Money(amountMinor, currency),
    type = TransactionType.entries.firstOrNull { it.name == type } ?: TransactionType.UNKNOWN,
    direction = Direction.entries.firstOrNull { it.name == direction } ?: Direction.NEUTRAL,
    category = TxnCategory.fromNameOrNull(category) ?: TxnCategory.UNCATEGORIZED,
    subcategory = subcategory,
    paymentMethod = PaymentMethod.entries.firstOrNull { it.name == paymentMethod } ?: PaymentMethod.UNKNOWN,
    occurredAt = Instant.parse(occurredAt),
    merchantId = merchantId,
    merchantName = merchantName,
    rawMerchant = rawMerchant,
    merchantConfidence = Confidence.of(merchantConfidence),
    accountId = accountId,
    maskedAccount = maskedAccount,
    bankCode = bankCode,
    upiId = upiId,
    balanceAfter = balanceAfterMinor?.let { Money(it, currency) },
    referenceNumber = referenceNumber,
    transactionId = transactionId,
    description = description,
    notes = notes,
    smsId = smsId,
    parserId = parserId,
    parserConfidence = Confidence.of(parserConfidence),
    source = SourceKind.entries.firstOrNull { it.name == source } ?: SourceKind.IMPORT,
    userVerified = userVerified,
    excludedFromStats = excludedFromStats,
    duplicateOfId = duplicateOfId,
    tagIds = tagIds,
    createdAt = createdAt?.let(Instant::parse) ?: Instant.EPOCH,
    updatedAt = updatedAt?.let(Instant::parse) ?: Instant.EPOCH,
)

@Serializable
data class MerchantDto(
    val id: Long = 0,
    val canonicalName: String,
    val normalizedKey: String,
    val defaultCategory: String = TxnCategory.UNCATEGORIZED.name,
    val defaultSubcategory: String? = null,
    val website: String? = null,
    val logoHint: String? = null,
    val isBuiltIn: Boolean = false,
    val isSubscriptionProvider: Boolean = false,
    val isBillProvider: Boolean = false,
    val isInvestmentProvider: Boolean = false,
    val transactionCount: Int = 0,
    val firstSeenAt: String? = null,
    val lastSeenAt: String? = null,
)

fun Merchant.toDto(): MerchantDto = MerchantDto(
    id = id,
    canonicalName = canonicalName,
    normalizedKey = normalizedKey,
    defaultCategory = defaultCategory.name,
    defaultSubcategory = defaultSubcategory,
    website = website,
    logoHint = logoHint,
    isBuiltIn = isBuiltIn,
    isSubscriptionProvider = isSubscriptionProvider,
    isBillProvider = isBillProvider,
    isInvestmentProvider = isInvestmentProvider,
    transactionCount = transactionCount,
    firstSeenAt = firstSeenAt?.toString(),
    lastSeenAt = lastSeenAt?.toString(),
)

fun MerchantDto.toDomain(): Merchant = Merchant(
    id = id,
    canonicalName = canonicalName,
    normalizedKey = normalizedKey,
    defaultCategory = TxnCategory.fromNameOrNull(defaultCategory) ?: TxnCategory.UNCATEGORIZED,
    defaultSubcategory = defaultSubcategory,
    website = website,
    logoHint = logoHint,
    isBuiltIn = isBuiltIn,
    isSubscriptionProvider = isSubscriptionProvider,
    isBillProvider = isBillProvider,
    isInvestmentProvider = isInvestmentProvider,
    transactionCount = transactionCount,
    firstSeenAt = firstSeenAt?.let(Instant::parse),
    lastSeenAt = lastSeenAt?.let(Instant::parse),
)

@Serializable
data class AccountDto(
    val id: Long = 0,
    val displayName: String,
    val type: String,
    val bankCode: String? = null,
    val bankName: String? = null,
    val maskedNumber: String? = null,
    val currency: String = Money.INR,
    val lastKnownBalanceMinor: Long? = null,
    val balanceAsOf: String? = null,
    val creditLimitMinor: Long? = null,
    val isArchived: Boolean = false,
    val colorSeed: Int = 0,
    val transactionCount: Int = 0,
    val firstSeenAt: String? = null,
    val lastSeenAt: String? = null,
)

fun Account.toDto(): AccountDto = AccountDto(
    id = id,
    displayName = displayName,
    type = type.name,
    bankCode = bankCode,
    bankName = bankName,
    maskedNumber = maskedNumber,
    currency = currency,
    lastKnownBalanceMinor = lastKnownBalance?.minor,
    balanceAsOf = balanceAsOf?.toString(),
    creditLimitMinor = creditLimit?.minor,
    isArchived = isArchived,
    colorSeed = colorSeed,
    transactionCount = transactionCount,
    firstSeenAt = firstSeenAt?.toString(),
    lastSeenAt = lastSeenAt?.toString(),
)

fun AccountDto.toDomain(): Account = Account(
    id = id,
    displayName = displayName,
    type = AccountType.entries.firstOrNull { it.name == type } ?: AccountType.UNKNOWN,
    bankCode = bankCode,
    bankName = bankName,
    maskedNumber = maskedNumber,
    currency = currency,
    lastKnownBalance = lastKnownBalanceMinor?.let { Money(it, currency) },
    balanceAsOf = balanceAsOf?.let(Instant::parse),
    creditLimit = creditLimitMinor?.let { Money(it, currency) },
    isArchived = isArchived,
    colorSeed = colorSeed,
    transactionCount = transactionCount,
    firstSeenAt = firstSeenAt?.let(Instant::parse),
    lastSeenAt = lastSeenAt?.let(Instant::parse),
)

@Serializable
data class SubscriptionDto(
    val id: Long = 0,
    val name: String,
    val merchantId: Long? = null,
    val amountMinor: Long,
    val currency: String = Money.INR,
    val recurrence: String = Recurrence.MONTHLY.name,
    val category: String = TxnCategory.SUBSCRIPTIONS.name,
    val firstChargedAt: String? = null,
    val lastChargedAt: String? = null,
    val nextExpectedAt: String? = null,
    val chargeCount: Int = 0,
    val status: String = Subscription.Status.ACTIVE.name,
    val confidence: Float = Confidence.MEDIUM.value,
    val userConfirmed: Boolean = false,
    val notes: String? = null,
    val previousAmountMinor: Long? = null,
    val priceChangedAt: String? = null,
)

fun Subscription.toDto(): SubscriptionDto = SubscriptionDto(
    id = id,
    name = name,
    merchantId = merchantId,
    amountMinor = amount.minor,
    currency = amount.currency,
    recurrence = recurrence.name,
    category = category.name,
    firstChargedAt = firstChargedAt?.toString(),
    lastChargedAt = lastChargedAt?.toString(),
    nextExpectedAt = nextExpectedAt?.toString(),
    chargeCount = chargeCount,
    status = status.name,
    confidence = confidence.value,
    userConfirmed = userConfirmed,
    notes = notes,
    previousAmountMinor = previousAmount?.minor,
    priceChangedAt = priceChangedAt?.toString(),
)

fun SubscriptionDto.toDomain(): Subscription = Subscription(
    id = id,
    name = name,
    merchantId = merchantId,
    amount = Money(amountMinor, currency),
    recurrence = Recurrence.entries.firstOrNull { it.name == recurrence } ?: Recurrence.MONTHLY,
    category = TxnCategory.fromNameOrNull(category) ?: TxnCategory.SUBSCRIPTIONS,
    firstChargedAt = firstChargedAt?.let(Instant::parse),
    lastChargedAt = lastChargedAt?.let(Instant::parse),
    nextExpectedAt = nextExpectedAt?.let(LocalDate::parse),
    chargeCount = chargeCount,
    status = Subscription.Status.entries.firstOrNull { it.name == status } ?: Subscription.Status.ACTIVE,
    confidence = Confidence.of(confidence),
    userConfirmed = userConfirmed,
    notes = notes,
    previousAmount = previousAmountMinor?.let { Money(it, currency) },
    priceChangedAt = priceChangedAt?.let(Instant::parse),
)

@Serializable
data class BillDto(
    val id: Long = 0,
    val name: String,
    val type: String,
    val merchantId: Long? = null,
    val accountId: Long? = null,
    val consumerNumber: String? = null,
    val lastAmountMinor: Long? = null,
    val averageAmountMinor: Long? = null,
    val currency: String = Money.INR,
    val recurrence: String = Recurrence.MONTHLY.name,
    val lastPaidAt: String? = null,
    val dueDate: String? = null,
    val dueDateIsEstimated: Boolean = true,
    val amountDueMinor: Long? = null,
    val status: String = Bill.Status.UPCOMING.name,
    val paymentCount: Int = 0,
    val reminderEnabled: Boolean = true,
    val reminderDaysBefore: Int = 3,
    val confidence: Float = Confidence.MEDIUM.value,
)

fun Bill.toDto(): BillDto = BillDto(
    id = id,
    name = name,
    type = type.name,
    merchantId = merchantId,
    accountId = accountId,
    consumerNumber = consumerNumber,
    lastAmountMinor = lastAmount?.minor,
    averageAmountMinor = averageAmount?.minor,
    currency = (lastAmount ?: averageAmount ?: amountDue)?.currency ?: Money.INR,
    recurrence = recurrence.name,
    lastPaidAt = lastPaidAt?.toString(),
    dueDate = dueDate?.toString(),
    dueDateIsEstimated = dueDateIsEstimated,
    amountDueMinor = amountDue?.minor,
    status = status.name,
    paymentCount = paymentCount,
    reminderEnabled = reminderEnabled,
    reminderDaysBefore = reminderDaysBefore,
    confidence = confidence.value,
)

fun BillDto.toDomain(): Bill = Bill(
    id = id,
    name = name,
    type = BillType.entries.firstOrNull { it.name == type } ?: BillType.OTHER,
    merchantId = merchantId,
    accountId = accountId,
    consumerNumber = consumerNumber,
    lastAmount = lastAmountMinor?.let { Money(it, currency) },
    averageAmount = averageAmountMinor?.let { Money(it, currency) },
    recurrence = Recurrence.entries.firstOrNull { it.name == recurrence } ?: Recurrence.MONTHLY,
    lastPaidAt = lastPaidAt?.let(Instant::parse),
    dueDate = dueDate?.let(LocalDate::parse),
    dueDateIsEstimated = dueDateIsEstimated,
    amountDue = amountDueMinor?.let { Money(it, currency) },
    status = Bill.Status.entries.firstOrNull { it.name == status } ?: Bill.Status.UPCOMING,
    paymentCount = paymentCount,
    reminderEnabled = reminderEnabled,
    reminderDaysBefore = reminderDaysBefore,
    confidence = Confidence.of(confidence),
)

@Serializable
data class InvestmentDto(
    val id: Long = 0,
    val name: String,
    val instrumentType: String,
    val folioOrAccount: String? = null,
    val provider: String? = null,
    val merchantId: Long? = null,
    val totalInvestedMinor: Long = 0,
    val totalRedeemedMinor: Long = 0,
    val currency: String = Money.INR,
    val manualCurrentValueMinor: Long? = null,
    val valueAsOf: String? = null,
    val units: Double? = null,
    val isSip: Boolean = false,
    val sipAmountMinor: Long? = null,
    val sipRecurrence: String = Recurrence.MONTHLY.name,
    val sipDayOfMonth: Int? = null,
    val nextExpectedAt: String? = null,
    val isActive: Boolean = true,
    val contributionCount: Int = 0,
    val firstInvestedAt: String? = null,
    val lastInvestedAt: String? = null,
)

fun Investment.toDto(): InvestmentDto = InvestmentDto(
    id = id,
    name = name,
    instrumentType = instrumentType.name,
    folioOrAccount = folioOrAccount,
    provider = provider,
    merchantId = merchantId,
    totalInvestedMinor = totalInvested.minor,
    totalRedeemedMinor = totalRedeemed.minor,
    currency = totalInvested.currency,
    manualCurrentValueMinor = manualCurrentValue?.minor,
    valueAsOf = valueAsOf?.toString(),
    units = units,
    isSip = isSip,
    sipAmountMinor = sipAmount?.minor,
    sipRecurrence = sipRecurrence.name,
    sipDayOfMonth = sipDayOfMonth,
    nextExpectedAt = nextExpectedAt?.toString(),
    isActive = isActive,
    contributionCount = contributionCount,
    firstInvestedAt = firstInvestedAt?.toString(),
    lastInvestedAt = lastInvestedAt?.toString(),
)

fun InvestmentDto.toDomain(): Investment = Investment(
    id = id,
    name = name,
    instrumentType = InstrumentType.entries.firstOrNull { it.name == instrumentType } ?: InstrumentType.OTHER,
    folioOrAccount = folioOrAccount,
    provider = provider,
    merchantId = merchantId,
    totalInvested = Money(totalInvestedMinor, currency),
    totalRedeemed = Money(totalRedeemedMinor, currency),
    manualCurrentValue = manualCurrentValueMinor?.let { Money(it, currency) },
    valueAsOf = valueAsOf?.let(Instant::parse),
    units = units,
    isSip = isSip,
    sipAmount = sipAmountMinor?.let { Money(it, currency) },
    sipRecurrence = Recurrence.entries.firstOrNull { it.name == sipRecurrence } ?: Recurrence.MONTHLY,
    sipDayOfMonth = sipDayOfMonth,
    nextExpectedAt = nextExpectedAt?.let(LocalDate::parse),
    isActive = isActive,
    contributionCount = contributionCount,
    firstInvestedAt = firstInvestedAt?.let(Instant::parse),
    lastInvestedAt = lastInvestedAt?.let(Instant::parse),
)

@Serializable
data class TagDto(
    val id: Long = 0,
    val name: String,
    val colorSeed: Int = 0,
    val usageCount: Int = 0,
)

fun Tag.toDto(): TagDto = TagDto(id = id, name = name, colorSeed = colorSeed, usageCount = usageCount)

fun TagDto.toDomain(): Tag = Tag(id = id, name = name, colorSeed = colorSeed, usageCount = usageCount)

@Serializable
data class UserRuleConditionDto(val field: String, val operator: String, val value: String)

@Serializable
data class UserRuleActionDto(val target: String, val value: String)

@Serializable
data class UserRuleDto(
    val id: Long = 0,
    val name: String,
    val enabled: Boolean = true,
    val priority: Int = 100,
    val conditions: List<UserRuleConditionDto> = emptyList(),
    val actions: List<UserRuleActionDto> = emptyList(),
    val matchAllConditions: Boolean = true,
    val stopOnMatch: Boolean = false,
    val createdAt: String? = null,
    val timesApplied: Int = 0,
)

fun UserRule.toDto(): UserRuleDto = UserRuleDto(
    id = id,
    name = name,
    enabled = enabled,
    priority = priority,
    conditions = conditions.map { UserRuleConditionDto(it.field.name, it.operator.name, it.value) },
    actions = actions.map { UserRuleActionDto(it.target.name, it.value) },
    matchAllConditions = matchAllConditions,
    stopOnMatch = stopOnMatch,
    createdAt = createdAt.toString(),
    timesApplied = timesApplied,
)

fun UserRuleDto.toDomain(): UserRule = UserRule(
    id = id,
    name = name,
    enabled = enabled,
    priority = priority,
    conditions = conditions.map {
        UserRule.Condition(
            field = UserRule.Condition.Field.entries.firstOrNull { f -> f.name == it.field }
                ?: UserRule.Condition.Field.DESCRIPTION,
            operator = UserRule.Condition.Operator.entries.firstOrNull { o -> o.name == it.operator }
                ?: UserRule.Condition.Operator.CONTAINS,
            value = it.value,
        )
    },
    actions = actions.map {
        UserRule.Action(
            target = UserRule.Action.Target.entries.firstOrNull { t -> t.name == it.target }
                ?: UserRule.Action.Target.SET_NOTE,
            value = it.value,
        )
    },
    matchAllConditions = matchAllConditions,
    stopOnMatch = stopOnMatch,
    createdAt = createdAt?.let(Instant::parse) ?: Instant.EPOCH,
    timesApplied = timesApplied,
)
