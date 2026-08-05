package com.lifeledger.sms.lex

import com.lifeledger.core.model.BillType
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.InstrumentType
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType

/**
 * Keyword vocabulary of Indian bank/service SMS.
 *
 * The engine is rule-based by design: the vocabulary banks use is small, stable and
 * public, and a lexicon plus regex beats a model that would need to ship weights, burn
 * battery and still be wrong in ways nobody can debug. Everything here is ordered so that
 * the most specific signal wins — `salary credited` must not be read as a plain credit.
 */
object Lexicon {

    // -------------------------------------------------------------- direction

    private val DEBIT_WORDS = listOf(
        "debited", "debit", "withdrawn", "spent", "paid", "payment of", "purchase",
        "deducted", "charged", "sent to", "transferred to", "txn of", "used for",
        "has been used", "utilised", "utilized",
    )

    private val CREDIT_WORDS = listOf(
        "credited", "credit", "received", "deposited", "refunded", "reversed",
        "added to", "cashback of", "has been credited", "transferred from",
    )

    /**
     * Direction of money for this message.
     *
     * Where both vocabularies appear (`Rs.500 debited ... credited to beneficiary`), the
     * *earliest* match wins, because Indian bank alerts always lead with the event as it
     * affects the account holder.
     */
    fun direction(body: String): Direction {
        val lower = body.lowercase()
        val debitAt = DEBIT_WORDS.mapNotNull { w -> lower.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        val creditAt = CREDIT_WORDS.mapNotNull { w -> lower.indexOf(w).takeIf { it >= 0 } }.minOrNull()
        return when {
            debitAt == null && creditAt == null -> Direction.NEUTRAL
            debitAt == null -> Direction.CREDIT
            creditAt == null -> Direction.DEBIT
            debitAt <= creditAt -> Direction.DEBIT
            else -> Direction.CREDIT
        }
    }

    // ------------------------------------------------------------------ rails

    private val METHOD_WORDS: List<Pair<PaymentMethod, List<String>>> = listOf(
        PaymentMethod.UPI to listOf("upi", "vpa", "@ok", "@ybl", "@paytm", "@axl", "bhim"),
        PaymentMethod.IMPS to listOf("imps"),
        PaymentMethod.NEFT to listOf("neft"),
        PaymentMethod.RTGS to listOf("rtgs"),
        PaymentMethod.ATM to listOf("atm", "cash withdrawal", "cash wdl"),
        PaymentMethod.NACH_ECS to listOf("nach", "ecs", "e-mandate", "emandate"),
        PaymentMethod.AUTOPAY to listOf("autopay", "auto pay", "auto-debit", "auto debit"),
        PaymentMethod.STANDING_INSTRUCTION to listOf("standing instruction", " si ", "s.i."),
        PaymentMethod.CARD_CREDIT to listOf("credit card", "cc no", "creditcard"),
        PaymentMethod.CARD_DEBIT to listOf("debit card", "debitcard"),
        PaymentMethod.CHEQUE to listOf("cheque", "chq"),
        PaymentMethod.NET_BANKING to listOf("netbanking", "net banking", "internet banking"),
        PaymentMethod.WALLET to listOf("wallet"),
        PaymentMethod.CASH to listOf("cash deposit", "cash dep"),
    )

    fun paymentMethod(body: String): PaymentMethod {
        val lower = " ${body.lowercase()} "
        return METHOD_WORDS.firstOrNull { (_, words) -> words.any { lower.contains(it) } }
            ?.first
            ?: PaymentMethod.UNKNOWN
    }

    // ------------------------------------------------------------------ types

    /**
     * Type keywords, most specific first. The first entry whose keywords appear wins, so
     * ordering here *is* the precedence rule.
     */
    private val TYPE_WORDS: List<Pair<TransactionType, List<String>>> = listOf(
        TransactionType.SALARY to listOf("salary", "sal cr", "sal-", "payroll", "wages", "stipend"),
        TransactionType.SIP to listOf("sip", "systematic investment"),
        TransactionType.EMI to listOf("emi", "instalment", "installment"),
        TransactionType.INSURANCE_PREMIUM to listOf("premium", "policy no", "insurance", "lic ", "term plan"),
        TransactionType.DIVIDEND to listOf("dividend", "div warrant"),
        TransactionType.INTEREST to listOf("interest credited", "int credited", "int.pd", "interest paid", "int cr"),
        TransactionType.CASHBACK to listOf("cashback", "cash back", "reward credited"),
        TransactionType.REFUND to listOf("refund", "refunded"),
        TransactionType.REVERSAL to listOf("reversed", "reversal", "chargeback"),
        TransactionType.ATM_WITHDRAWAL to listOf("atm", "cash withdrawal", "cash wdl"),
        TransactionType.CASH_DEPOSIT to listOf("cash deposit", "cash dep", "cdm"),
        TransactionType.CREDIT_CARD_PAYMENT to listOf("credit card payment", "card payment received", "payment received towards"),
        TransactionType.LOAN_REPAYMENT to listOf("loan repayment", "loan account", "repayment of loan"),
        TransactionType.LOAN_DISBURSAL to listOf("loan disbursed", "disbursal", "loan amount credited"),
        TransactionType.INVESTMENT to listOf(
            "mutual fund", "folio", "nav", "units allotted", "purchase of units",
            "demat", "nps", "ppf", "elss", "bond", "sovereign gold",
        ),
        TransactionType.REDEMPTION to listOf("redeemed", "redemption", "units redeemed"),
        TransactionType.MATURITY to listOf("matured", "maturity"),
        TransactionType.RECHARGE to listOf("recharge", "topup", "top-up", "top up"),
        TransactionType.BILL_PAYMENT to listOf("bill paid", "bill payment", "bbps", "towards bill", "bill of"),
        TransactionType.BILL_DUE to listOf("bill due", "due on", "is due", "payment due", "outstanding of"),
        TransactionType.TAX to listOf("income tax", "gst", "tds", "advance tax", "challan"),
        TransactionType.RENT to listOf("rent"),
        TransactionType.SUBSCRIPTION to listOf("subscription", "renewed", "auto-renew", "auto renewal"),
        TransactionType.FEE_OR_CHARGE to listOf("charges", "fee", "penalty", "late fee", "annual fee", "gst on"),
        TransactionType.DONATION to listOf("donation", "donated"),
        TransactionType.OTP to listOf("otp", "one time password", "verification code", "do not share"),
        TransactionType.DELIVERY to listOf("out for delivery", "delivered", "shipped", "dispatched", "courier", "tracking"),
        TransactionType.BOOKING to listOf("pnr", "booking confirmed", "ticket", "boarding", "check-in", "itinerary", "seat"),
        TransactionType.APPOINTMENT to listOf("appointment", "consultation", "doctor", "clinic visit", "slot booked"),
        TransactionType.BALANCE_INFO to listOf("available balance is", "bal enquiry", "balance enquiry"),
        TransactionType.PROMOTIONAL to listOf("offer", "discount", "sale ends", "click here", "apply now", "congratulations you"),
    )

    /**
     * Semantic type for a message, given the [Direction] already established.
     *
     * When no keyword fires, the direction decides: a debit is a purchase, a credit is a
     * transfer in. Those are the honest defaults — better than `UNKNOWN`, and the user can
     * always correct them, which feeds the rule engine.
     */
    fun transactionType(body: String, direction: Direction): TransactionType {
        val lower = " ${body.lowercase()} "
        val match = TYPE_WORDS.firstOrNull { (_, words) -> words.any { lower.contains(it) } }?.first
        if (match != null && isDirectionCompatible(match, direction)) return match
        return when (direction) {
            Direction.DEBIT -> TransactionType.PURCHASE
            Direction.CREDIT -> TransactionType.TRANSFER_IN
            Direction.NEUTRAL -> match ?: TransactionType.UNKNOWN
        }
    }

    /**
     * Guards against absurd pairings such as a *credit* classified as an EMI payment.
     * Non-financial types are always compatible: they carry no direction of their own.
     */
    private fun isDirectionCompatible(type: TransactionType, direction: Direction): Boolean = when {
        !type.isFinancial -> true
        direction == Direction.NEUTRAL -> true
        else -> type.direction == direction
    }

    // ------------------------------------------------------------ instruments

    private val INSTRUMENT_WORDS: List<Pair<InstrumentType, List<String>>> = listOf(
        InstrumentType.SIP to listOf("sip", "systematic investment"),
        InstrumentType.MUTUAL_FUND to listOf("mutual fund", "folio", "nav", "units allotted", "elss", "amc"),
        InstrumentType.ETF to listOf("etf"),
        InstrumentType.STOCK to listOf("shares", "equity", "demat", "nse", "bse", "stock"),
        InstrumentType.BOND to listOf("bond", "debenture", "ncd", "g-sec"),
        InstrumentType.GOLD to listOf("gold", "sgb", "sovereign gold", "digital gold"),
        InstrumentType.PPF to listOf("ppf", "public provident"),
        InstrumentType.NPS to listOf("nps", "pension scheme"),
        InstrumentType.EPF to listOf("epf", "provident fund", "uan"),
        InstrumentType.FIXED_DEPOSIT to listOf("fixed deposit", " fd ", "fd no", "term deposit"),
        InstrumentType.RECURRING_DEPOSIT to listOf("recurring deposit", " rd ", "rd no"),
        InstrumentType.INSURANCE_LINKED to listOf("ulip"),
    )

    fun instrumentType(body: String): InstrumentType? {
        val lower = " ${body.lowercase()} "
        return INSTRUMENT_WORDS.firstOrNull { (_, words) -> words.any { lower.contains(it) } }?.first
    }

    // ------------------------------------------------------------------ bills

    private val BILL_WORDS: List<Pair<BillType, List<String>>> = listOf(
        BillType.ELECTRICITY to listOf("electricity", "power bill", "bescom", "mseb", "tneb", "kseb", "adani electricity", "tata power", "torrent power", "bses"),
        BillType.GAS to listOf("gas bill", "lpg", "indane", "hp gas", "bharatgas", "mahanagar gas", "igl "),
        BillType.WATER to listOf("water bill", "jal board", "water charges"),
        BillType.BROADBAND to listOf("broadband", "fiber", "fibre", "jiofiber", "act fibernet", "airtel xstream"),
        BillType.INTERNET to listOf("internet bill", "data plan"),
        BillType.MOBILE to listOf("mobile bill", "postpaid", "prepaid recharge", "airtel", "jio", "vi ", "vodafone", "bsnl"),
        BillType.DTH to listOf("dth", "tata play", "tata sky", "dish tv", "d2h", "sun direct"),
        BillType.PROPERTY_TAX to listOf("property tax", "municipal"),
        BillType.MAINTENANCE to listOf("maintenance charges", "society maintenance"),
        BillType.CREDIT_CARD to listOf("credit card bill", "card statement", "total amount due"),
        BillType.INSURANCE to listOf("premium due", "policy renewal"),
        BillType.LOAN_EMI to listOf("emi due", "loan emi"),
    )

    fun billType(body: String): BillType? {
        val lower = " ${body.lowercase()} "
        return BILL_WORDS.firstOrNull { (_, words) -> words.any { lower.contains(it) } }?.first
    }

    // ------------------------------------------------------------ promotional

    private val PROMO_MARKERS = listOf(
        "click here", "t&c apply", "terms and conditions apply", "unsubscribe", "to opt out",
        "hurry", "limited period", "exclusive offer", "flat 50%", "shop now", "buy now",
        "download the app", "refer and earn", "lucky winner", "you have won",
    )

    /**
     * True when the message is marketing rather than a record of something that happened.
     * Promotional messages are stored but never become transactions.
     */
    fun looksPromotional(body: String): Boolean {
        val lower = body.lowercase()
        if (PROMO_MARKERS.count { lower.contains(it) } >= 1 && !lower.contains("debited") &&
            !lower.contains("credited")
        ) {
            return true
        }
        return false
    }

    private val OTP_MARKERS = listOf("otp", "one time password", "verification code", "security code")

    /** True when the message exists only to deliver a code. */
    fun looksLikeOtp(body: String): Boolean {
        val lower = body.lowercase()
        if (OTP_MARKERS.none { lower.contains(it) }) return false
        // "OTP for txn of Rs.500" is a real transaction alert, not a bare code delivery.
        return !lower.contains("debited") && !lower.contains("credited")
    }
}
