package com.lifeledger.core.model

/**
 * Direction of money relative to the user.
 *
 * [NEUTRAL] covers events that carry no value transfer — OTP notifications, delivery
 * updates, balance enquiries — which Life Ledger still records as life events.
 */
enum class Direction { CREDIT, DEBIT, NEUTRAL }

/**
 * The *semantic* nature of an event: what happened, not how the money moved.
 * Pairs with [PaymentMethod] (the rail) and [TxnCategory] (the spending bucket).
 */
enum class TransactionType(val direction: Direction, val isFinancial: Boolean = true) {
    SALARY(Direction.CREDIT),
    BUSINESS_INCOME(Direction.CREDIT),
    INTEREST(Direction.CREDIT),
    DIVIDEND(Direction.CREDIT),
    CASHBACK(Direction.CREDIT),
    REFUND(Direction.CREDIT),
    REVERSAL(Direction.CREDIT),
    CASH_DEPOSIT(Direction.CREDIT),
    LOAN_DISBURSAL(Direction.CREDIT),
    MATURITY(Direction.CREDIT),
    REDEMPTION(Direction.CREDIT),

    PURCHASE(Direction.DEBIT),
    INVESTMENT(Direction.DEBIT),
    SIP(Direction.DEBIT),
    EMI(Direction.DEBIT),
    LOAN_REPAYMENT(Direction.DEBIT),
    INSURANCE_PREMIUM(Direction.DEBIT),
    SUBSCRIPTION(Direction.DEBIT),
    BILL_PAYMENT(Direction.DEBIT),
    RECHARGE(Direction.DEBIT),
    TAX(Direction.DEBIT),
    RENT(Direction.DEBIT),
    FEE_OR_CHARGE(Direction.DEBIT),
    ATM_WITHDRAWAL(Direction.DEBIT),
    CREDIT_CARD_PAYMENT(Direction.DEBIT),
    DONATION(Direction.DEBIT),

    TRANSFER_IN(Direction.CREDIT),
    TRANSFER_OUT(Direction.DEBIT),

    // Non-financial life events: they hold no amount but they do belong on the timeline.
    OTP(Direction.NEUTRAL, isFinancial = false),
    DELIVERY(Direction.NEUTRAL, isFinancial = false),
    BOOKING(Direction.NEUTRAL, isFinancial = false),
    APPOINTMENT(Direction.NEUTRAL, isFinancial = false),
    BILL_DUE(Direction.NEUTRAL, isFinancial = false),
    BALANCE_INFO(Direction.NEUTRAL, isFinancial = false),
    PROMOTIONAL(Direction.NEUTRAL, isFinancial = false),

    UNKNOWN(Direction.NEUTRAL, isFinancial = false);

    val isIncome: Boolean get() = direction == Direction.CREDIT && isFinancial
    val isExpense: Boolean get() = direction == Direction.DEBIT && isFinancial
}

/** The rail the money travelled on. */
enum class PaymentMethod {
    UPI,
    NEFT,
    RTGS,
    IMPS,
    CARD_CREDIT,
    CARD_DEBIT,
    CARD_PREPAID,
    NET_BANKING,
    ATM,
    CASH,
    CHEQUE,
    WALLET,
    AUTOPAY,
    STANDING_INSTRUCTION,
    NACH_ECS,
    UNKNOWN,
}

/** Top-level spending bucket. Subcategories are free-form strings owned by the category rules. */
enum class TxnCategory(val displayName: String) {
    INCOME("Income"),
    FOOD("Food & Dining"),
    GROCERIES("Groceries"),
    SHOPPING("Shopping"),
    TRAVEL("Travel"),
    TRANSPORT("Transport"),
    FUEL("Fuel"),
    HEALTHCARE("Healthcare"),
    ENTERTAINMENT("Entertainment"),
    SUBSCRIPTIONS("Subscriptions"),
    UTILITIES("Utilities"),
    RENT("Rent & Housing"),
    EDUCATION("Education"),
    INVESTMENTS("Investments"),
    INSURANCE("Insurance"),
    LOANS("Loans & EMI"),
    TAXES("Taxes"),
    GOVERNMENT("Government"),
    CHARITY("Charity"),
    PERSONAL_CARE("Personal Care"),
    HOME("Home & Maintenance"),
    PETS("Pets"),
    GIFTS("Gifts"),
    CASH("Cash"),
    FEES("Fees & Charges"),
    TRANSFERS("Transfers"),
    MISC("Miscellaneous"),
    UNCATEGORIZED("Uncategorized");

    companion object {
        fun fromNameOrNull(value: String?): TxnCategory? =
            value?.let { name -> entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }
    }
}

enum class AccountType {
    SAVINGS,
    CURRENT,
    CREDIT_CARD,
    DEBIT_CARD,
    PREPAID_CARD,
    WALLET,
    LOAN,
    DEMAT,
    NPS,
    PPF,
    FIXED_DEPOSIT,
    RECURRING_DEPOSIT,
    UNKNOWN,
}

enum class InstrumentType(val displayName: String) {
    MUTUAL_FUND("Mutual Fund"),
    SIP("SIP"),
    STOCK("Stocks"),
    ETF("ETF"),
    BOND("Bonds"),
    GOLD("Gold"),
    PPF("PPF"),
    NPS("NPS"),
    EPF("EPF"),
    FIXED_DEPOSIT("Fixed Deposit"),
    RECURRING_DEPOSIT("Recurring Deposit"),
    INSURANCE_LINKED("ULIP"),
    CRYPTO("Crypto"),
    OTHER("Other"),
}

enum class BillType(val displayName: String) {
    ELECTRICITY("Electricity"),
    GAS("Gas"),
    WATER("Water"),
    INTERNET("Internet"),
    BROADBAND("Broadband"),
    MOBILE("Mobile"),
    DTH("DTH"),
    PROPERTY_TAX("Property Tax"),
    MAINTENANCE("Maintenance"),
    CREDIT_CARD("Credit Card"),
    LOAN_EMI("Loan EMI"),
    INSURANCE("Insurance"),
    OTHER("Other"),
}

/** How often a subscription, bill or SIP repeats. */
enum class Recurrence(val approxDays: Int) {
    DAILY(1),
    WEEKLY(7),
    FORTNIGHTLY(14),
    MONTHLY(30),
    BIMONTHLY(61),
    QUARTERLY(91),
    HALF_YEARLY(182),
    YEARLY(365),
    IRREGULAR(0),
    ONE_OFF(0);

    val isRecurring: Boolean get() = approxDays > 0
}

enum class TimelineEventType {
    TRANSACTION,
    INVESTMENT,
    SUBSCRIPTION_CHARGE,
    BILL_DUE,
    BILL_PAID,
    DELIVERY,
    BOOKING,
    APPOINTMENT,
    OTP,
    INSIGHT,
    MILESTONE,
    NOTE,
}

enum class InsightKind {
    SPEND_TREND,
    CATEGORY_SPIKE,
    NEW_MERCHANT,
    RECURRING_DETECTED,
    SUBSCRIPTION_PRICE_CHANGE,
    SAVINGS_RATE,
    INCOME_PATTERN,
    INVESTMENT_STREAK,
    BILL_INCREASE,
    LARGE_TRANSACTION,
    UNUSED_SUBSCRIPTION,
    LIFE_PATTERN,
    ANOMALY,
}

enum class InsightSeverity { INFO, NOTABLE, WARNING, CELEBRATION }

/** Buckets used across statistics, charts and the "period" selector. */
enum class PeriodGranularity { DAY, WEEK, MONTH, QUARTER, YEAR }

enum class SourceKind {
    /** Parsed from an on-device SMS. The only source shipped today. */
    SMS,
    /** Created or corrected by the user. */
    MANUAL,
    /** Brought in through CSV/XLSX/JSON/XML import. */
    IMPORT,

    // Reserved for future Life OS data sources — see docs/ROADMAP.md.
    EMAIL,
    STATEMENT,
    CALENDAR,
    RECEIPT_OCR,
    HEALTH,
    LOCATION,
}
