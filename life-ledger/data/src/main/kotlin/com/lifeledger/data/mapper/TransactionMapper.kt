package com.lifeledger.data.mapper

import com.lifeledger.core.database.entity.TransactionEntity
import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SourceKind
import com.lifeledger.core.model.Transaction
import com.lifeledger.core.model.TransactionType
import com.lifeledger.core.model.TxnCategory
import java.time.Instant

/**
 * Row ⇄ domain conversion for transactions.
 *
 * Enums are stored as their `name` and read back defensively: a row written by a newer
 * build that added an enum constant must not crash an older one, so an unrecognised value
 * degrades to the neutral member rather than throwing. That matters here because the
 * database outlives any single version of the app and is the user's only copy.
 */
fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    amount = Money(amountMinor, currency),
    type = enumOrDefault(type, TransactionType.UNKNOWN),
    direction = enumOrDefault(direction, Direction.NEUTRAL),
    category = enumOrDefault(category, TxnCategory.UNCATEGORIZED),
    subcategory = subcategory,
    paymentMethod = enumOrDefault(paymentMethod, PaymentMethod.UNKNOWN),
    occurredAt = Instant.ofEpochMilli(occurredAt),
    merchantId = merchantId,
    merchantName = merchantName,
    rawMerchant = rawMerchant,
    merchantConfidence = Confidence(merchantConfidence),
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
    parserConfidence = Confidence(parserConfidence),
    source = enumOrDefault(source, SourceKind.SMS),
    userVerified = userVerified,
    excludedFromStats = excludedFromStats,
    duplicateOfId = duplicateOfId,
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = Instant.ofEpochMilli(updatedAt),
)

/**
 * @param dedupeHash carried separately because it is derived during ingestion, not from the
 *   domain model — a transaction the user typed by hand has no SMS to hash.
 */
fun Transaction.toEntity(dedupeHash: String? = null): TransactionEntity = TransactionEntity(
    id = id,
    amountMinor = amount.minor,
    currency = amount.currency,
    type = type.name,
    direction = direction.name,
    category = category.name,
    subcategory = subcategory,
    paymentMethod = paymentMethod.name,
    occurredAt = occurredAt.toEpochMilli(),
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
    createdAt = if (createdAt == Instant.EPOCH) System.currentTimeMillis() else createdAt.toEpochMilli(),
    updatedAt = System.currentTimeMillis(),
    dedupeHash = dedupeHash,
    searchBlob = buildSearchBlob(),
)

/**
 * The denormalised text the FTS index is built over.
 *
 * Built in the mapper rather than by a database trigger for one reason: it needs the
 * *display* forms of enums and the normalised merchant name, which only Kotlin knows. A
 * trigger would have to duplicate that vocabulary in SQL and would drift the first time a
 * category was renamed.
 */
private fun Transaction.buildSearchBlob(): String = listOfNotNull(
    merchantName,
    rawMerchant,
    description,
    notes,
    category.displayName,
    type.name.replace('_', ' '),
    paymentMethod.name.replace('_', ' '),
    maskedAccount,
    bankCode,
    upiId,
    referenceNumber,
).joinToString(" ").lowercase()

/** Reads an enum by name, falling back rather than throwing on an unknown value. */
internal inline fun <reified T : Enum<T>> enumOrDefault(name: String?, fallback: T): T =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback
