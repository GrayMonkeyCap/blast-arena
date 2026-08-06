package com.lifeledger.core.database.converter

import androidx.room.TypeConverter
import com.lifeledger.core.model.AccountType
import com.lifeledger.core.model.Bill
import com.lifeledger.core.model.BillType
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.InsightKind
import com.lifeledger.core.model.InsightSeverity
import com.lifeledger.core.model.InstrumentType
import com.lifeledger.core.model.InvestmentTransaction
import com.lifeledger.core.model.ParseLogEntry
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.Recurrence
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.core.model.SourceKind
import com.lifeledger.core.model.Subscription
import com.lifeledger.core.model.TimelineEventType
import com.lifeledger.core.model.TransactionType
import com.lifeledger.core.model.TxnCategory
import com.lifeledger.core.model.UserRule
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Every conversion between a Kotlin type and a SQLite column.
 *
 * Three rules hold throughout and explain most of what follows:
 *
 * 1. **Timestamps are epoch millis, dates are epoch days.** Both are stored as INTEGER so
 *    that range predicates use an index and `strftime` can bucket them in SQL; a text date
 *    would force every aggregate through a full scan and a per-row parse.
 * 2. **Enums are stored as `name`, never as an ordinal.** An ordinal silently changes meaning
 *    the moment someone reorders an enum, and the corruption is invisible until a user
 *    notices their groceries turned into insurance.
 * 3. **Decoding never throws.** An unknown enum name — the signature of a downgrade or a
 *    hand-edited database — yields `null` rather than killing the query that touched the row.
 *    `enumValueOf` would take the whole screen down for one bad cell.
 *
 * Entity columns hold enums as `String` directly, which keeps the exported schema and the
 * dynamic query builder readable. The enum converters below are therefore used for *query
 * parameters*: they let a DAO signature stay type-safe (`direction: Direction`) while the
 * column stays plain TEXT.
 */
class Converters {

    // ---- Time -----------------------------------------------------------------------------

    @TypeConverter
    fun fromInstant(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun toLocalDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    // ---- Collections ----------------------------------------------------------------------

    /**
     * Id lists are joined with commas rather than stored as JSON: they are opaque payloads
     * that are always read back whole, and the comma form stays legible in a `sqlite3`
     * session and in the exported schema's default values.
     */
    @TypeConverter
    fun fromLongList(value: List<Long>?): String? =
        value?.joinToString(separator = LIST_SEPARATOR)

    @TypeConverter
    fun toLongList(value: String?): List<Long>? = value?.let { raw ->
        if (raw.isEmpty()) {
            emptyList()
        } else {
            // Silently drop anything unparseable: a truncated id list must not stop the row
            // it belongs to from loading.
            raw.split(LIST_SEPARATOR).mapNotNull(String::toLongOrNull)
        }
    }

    @TypeConverter
    fun fromStringMap(value: Map<String, String>?): String? =
        value?.let { json.encodeToString(MAP_SERIALIZER, it) }

    @TypeConverter
    fun toStringMap(value: String?): Map<String, String>? = value?.let { raw ->
        runCatching { json.decodeFromString(MAP_SERIALIZER, raw) }.getOrDefault(emptyMap())
    }

    // ---- User rules -----------------------------------------------------------------------

    @TypeConverter
    fun fromConditions(value: List<UserRule.Condition>?): String? =
        value?.let { json.encodeToString(CONDITION_SERIALIZER, it.map(ConditionDto::of)) }

    @TypeConverter
    fun toConditions(value: String?): List<UserRule.Condition>? = value?.let { raw ->
        runCatching { json.decodeFromString(CONDITION_SERIALIZER, raw) }
            .getOrDefault(emptyList())
            .map(ConditionDto::toDomain)
    }

    @TypeConverter
    fun fromActions(value: List<UserRule.Action>?): String? =
        value?.let { json.encodeToString(ACTION_SERIALIZER, it.map(ActionDto::of)) }

    @TypeConverter
    fun toActions(value: String?): List<UserRule.Action>? = value?.let { raw ->
        runCatching { json.decodeFromString(ACTION_SERIALIZER, raw) }
            .getOrDefault(emptyList())
            .mapNotNull(ActionDto::toDomain)
    }

    // ---- Enums ----------------------------------------------------------------------------

    @TypeConverter
    fun fromDirection(value: Direction?): String? = value?.name

    @TypeConverter
    fun toDirection(value: String?): Direction? = decode<Direction>(value)

    @TypeConverter
    fun fromTransactionType(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? = decode<TransactionType>(value)

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod?): String? = value?.name

    @TypeConverter
    fun toPaymentMethod(value: String?): PaymentMethod? = decode<PaymentMethod>(value)

    @TypeConverter
    fun fromTxnCategory(value: TxnCategory?): String? = value?.name

    @TypeConverter
    fun toTxnCategory(value: String?): TxnCategory? = decode<TxnCategory>(value)

    @TypeConverter
    fun fromAccountType(value: AccountType?): String? = value?.name

    @TypeConverter
    fun toAccountType(value: String?): AccountType? = decode<AccountType>(value)

    @TypeConverter
    fun fromInstrumentType(value: InstrumentType?): String? = value?.name

    @TypeConverter
    fun toInstrumentType(value: String?): InstrumentType? = decode<InstrumentType>(value)

    @TypeConverter
    fun fromBillType(value: BillType?): String? = value?.name

    @TypeConverter
    fun toBillType(value: String?): BillType? = decode<BillType>(value)

    @TypeConverter
    fun fromRecurrence(value: Recurrence?): String? = value?.name

    @TypeConverter
    fun toRecurrence(value: String?): Recurrence? = decode<Recurrence>(value)

    @TypeConverter
    fun fromTimelineEventType(value: TimelineEventType?): String? = value?.name

    @TypeConverter
    fun toTimelineEventType(value: String?): TimelineEventType? = decode<TimelineEventType>(value)

    @TypeConverter
    fun fromInsightKind(value: InsightKind?): String? = value?.name

    @TypeConverter
    fun toInsightKind(value: String?): InsightKind? = decode<InsightKind>(value)

    @TypeConverter
    fun fromInsightSeverity(value: InsightSeverity?): String? = value?.name

    @TypeConverter
    fun toInsightSeverity(value: String?): InsightSeverity? = decode<InsightSeverity>(value)

    @TypeConverter
    fun fromSourceKind(value: SourceKind?): String? = value?.name

    @TypeConverter
    fun toSourceKind(value: String?): SourceKind? = decode<SourceKind>(value)

    @TypeConverter
    fun fromProcessingStatus(value: SmsRecord.ProcessingStatus?): String? = value?.name

    @TypeConverter
    fun toProcessingStatus(value: String?): SmsRecord.ProcessingStatus? =
        decode<SmsRecord.ProcessingStatus>(value)

    @TypeConverter
    fun fromParseOutcome(value: ParseLogEntry.Outcome?): String? = value?.name

    @TypeConverter
    fun toParseOutcome(value: String?): ParseLogEntry.Outcome? = decode<ParseLogEntry.Outcome>(value)

    @TypeConverter
    fun fromInvestmentKind(value: InvestmentTransaction.Kind?): String? = value?.name

    @TypeConverter
    fun toInvestmentKind(value: String?): InvestmentTransaction.Kind? =
        decode<InvestmentTransaction.Kind>(value)

    @TypeConverter
    fun fromSubscriptionStatus(value: Subscription.Status?): String? = value?.name

    @TypeConverter
    fun toSubscriptionStatus(value: String?): Subscription.Status? = decode<Subscription.Status>(value)

    @TypeConverter
    fun fromBillStatus(value: Bill.Status?): String? = value?.name

    @TypeConverter
    fun toBillStatus(value: String?): Bill.Status? = decode<Bill.Status>(value)

    private companion object {
        const val LIST_SEPARATOR = ","

        val json = Json {
            // Forward compatibility: a column written by a newer build that added a key must
            // still load here rather than throwing on the unknown field.
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        val MAP_SERIALIZER = MapSerializer(String.serializer(), String.serializer())

        val CONDITION_SERIALIZER = ListSerializer(ConditionDto.serializer())

        val ACTION_SERIALIZER = ListSerializer(ActionDto.serializer())
    }
}

private inline fun <reified E : Enum<E>> decode(name: String?): E? =
    name?.let { raw -> enumValues<E>().firstOrNull { it.name == raw } }
