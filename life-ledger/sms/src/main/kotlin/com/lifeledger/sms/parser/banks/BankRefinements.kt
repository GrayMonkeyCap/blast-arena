package com.lifeledger.sms.parser.banks

import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParsedTransaction
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Corrections shared by the six large retail-bank parsers.
 *
 * These helpers deliberately live beside the parsers instead of in `SmsPatterns`: each one
 * encodes a habit of *this* group of issuers — SBI's separator-less dates, HDFC's `Info:`
 * narration, the `<counterparty> credited` clause ICICI and IDFC FIRST both borrowed from
 * the NPCI reference wording — and promoting them to the shared lexicon would silently
 * change how every other parser in the app reads a message. Anything here that later proves
 * universal can be promoted; nothing here may assume it already is.
 *
 * Everything is a pure function over the message text so a parser stays replayable against
 * stored history, and nothing throws: callers get `null` for "not present in this message".
 */
internal object BankRefinements {

    // ------------------------------------------------------------- rejections

    private val COLLECT_REQUEST = Regex(
        """\b(?:payment|collect|money)\s+request\b|\bhas\s+requested\s+(?:money|rs|inr|payment)|\brequesting\s+(?:money|payment)|\brequest(?:ed)?\s+you\s+to\s+pay\b""",
        RegexOption.IGNORE_CASE,
    )

    private val FUTURE_DEBIT = Regex(
        """\b(?:will|shall|would)\s+be\s+(?:debited|deducted|charged|auto[\s\-]?debited)\b|\bis\s+due\s+for\s+debit\b|\bwill\s+be\s+auto[\s\-]?(?:paid|renewed)\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Why this message must not become a transaction, or `null` when it may.
     *
     * Two shapes fool a keyword-driven engine badly. A UPI *collect request* reads exactly
     * like an incoming payment ("you have received a payment request of Rs.500") while no
     * money has moved, and a UPI-Autopay pre-debit notice ("Rs.649 will be debited on
     * 05-08-25") describes a debit that has not happened yet — booking either one invents
     * money and then double-counts when the real alert arrives a day later.
     */
    fun rejectionReason(body: String): String? = when {
        COLLECT_REQUEST.containsMatchIn(body) -> "collect request: no money has moved yet"
        FUTURE_DEBIT.containsMatchIn(body) -> "advance notice of a future debit"
        else -> null
    }

    // ------------------------------------------------------------------ dates

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12,
    )

    private val COMPACT_DATE = Regex("""\b(\d{1,2})([A-Za-z]{3})(\d{2,4})\b""")

    /**
     * A `DDMMMYY` date written without separators, as in SBI's `on 05Aug25`.
     *
     * `SmsPatterns.dateIn` can read this shape, but it tries the `d/m/y` numeric shape over
     * the whole body *first* — so an SBI alert that also carries a numeric date in its
     * trailer ("Next EMI due 05/09/25") dates the transaction to the wrong day. Pulling the
     * compact date out explicitly lets the SBI parser prefer the one attached to the event.
     *
     * Scans every candidate rather than only the first, because reference numbers such as
     * `12ABC34` match the shape but name no month.
     */
    fun parseCompactDate(text: String, referenceYear: Int = LocalDate.now().year): LocalDate? {
        for (match in COMPACT_DATE.findAll(text)) {
            val (day, monthName, year) = match.destructured
            val month = MONTHS[monthName.lowercase()] ?: continue
            val dayValue = day.toIntOrNull() ?: continue
            val yearValue = year.toIntOrNull()?.let { if (it >= 1000) it else 2000 + it } ?: continue
            if (yearValue !in referenceYear - 25..referenceYear + 25) continue
            val date = runCatching { LocalDate.of(yearValue, month, dayValue) }.getOrNull()
            if (date != null) return date
        }
        return null
    }

    /** Moves [instant] onto [date] while keeping its time of day, which the SMS rarely states. */
    fun withDate(instant: Instant, date: LocalDate, zone: ZoneId): Instant =
        LocalDateTime.of(date, instant.atZone(zone).toLocalTime()).atZone(zone).toInstant()

    // -------------------------------------------------------------- direction

    private val SENT = Regex("""(?:^|\b)(?:amt\s+)?sent\s*(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)
    private val RECEIVED = Regex("""(?:^|\b)received\s*(?:rs\.?|inr|₹)""", RegexOption.IGNORE_CASE)

    /**
     * Direction for HDFC's and Kotak's "Sent Rs.X … / Received Rs.X …" UPI alerts.
     *
     * Both banks lead with a bare verb that the shared lexicon only recognises in its
     * longer form ("sent to"), so the direction has to be re-derived from the opening
     * clause. Returns `null` when the message uses neither verb, leaving the generic
     * decision alone.
     */
    fun directionFromSentReceived(body: String): Direction? = when {
        SENT.containsMatchIn(body) -> Direction.DEBIT
        RECEIVED.containsMatchIn(body) -> Direction.CREDIT
        else -> null
    }

    // --------------------------------------------------------------- merchant

    /**
     * Tokens that follow a merchant name in these banks' alerts but are never part of it.
     * Cutting at the first one turns `SWIGGY Ref No 5123…` into `SWIGGY` without needing a
     * per-template terminator for every capture.
     */
    private val TRAILING_STOP_TOKENS = setOf(
        "on", "ref", "refno", "upi", "info", "avl", "avlbl", "bal", "balance", "lmt", "limit",
        "not", "if", "call", "sms", "block", "txn", "trxn", "trans", "transaction", "new",
        "your", "you", "thank", "thanks", "dispute", "report", "toll", "free", "customer",
        "care", "total", "a/c", "ac", "acct", "account", "via", "by", "using", "with", "rs",
        "rs.", "inr", "₹", "dt", "date", "at", "to", "from", "is", "was", "and", "will",
    )

    /**
     * The merchant name with the alert's boilerplate tail removed.
     *
     * Generic capture almost always over-reaches — the shared patterns stop at a small set
     * of terminators, and these banks put reference numbers, balances and anti-fraud
     * footers immediately after the payee. Trimming by token keeps multi-word names intact
     * ("ACME SOFTWARE PVT LTD") while discarding the noise that follows them.
     */
    fun stripTrailingNoise(raw: String?): String? {
        val flattened = raw?.replace(Regex("""[\r\n]+"""), " ")?.trim() ?: return null
        val kept = mutableListOf<String>()
        for (token in flattened.split(' ')) {
            if (token.isBlank()) continue
            val key = token.trim('.', ',', ';', ':', '-', '(', ')', '#', '*', '/', '!', '?').lowercase()
            if (key in TRAILING_STOP_TOKENS) break
            kept += token
        }
        val name = kept.joinToString(" ").trim(' ', '.', ',', ';', ':', '-', '*', '#', '/')
        return name.takeIf { it.length >= 2 && it.any(Char::isLetter) && it.length <= 60 }
    }

    /**
     * True when the "merchant" the generic patterns found is really the user's own bank.
     *
     * `debited from HDFC Bank XX1234` matches the shared `from <name>` pattern, and booking
     * the issuer as the counterparty would create a merchant every user transacts with
     * hundreds of times — the single worst thing that can happen to merchant analytics.
     */
    fun isSelfReference(name: String?, vararg bankWords: String): Boolean {
        val lower = name?.lowercase() ?: return false
        return bankWords.any { lower.contains(it.lowercase()) }
    }

    // ------------------------------------------------- narration / info field

    private val INFO_FIELD = Regex(
        """\b(?:info|inf|narration|remarks?|details?)\s*[:\-]\s*([^\r\n]{2,90})""",
        RegexOption.IGNORE_CASE,
    )

    private val INFO_TAIL_MARKERS = listOf(
        " avl ", " avl.", " avlbl", " available bal", " new bal", " clr bal", " a/c bal",
        " not you", " call ", " sms ", " to dispute", " ref ",
    )

    /**
     * The whole `Info:` narration, not just its first token.
     *
     * `SmsPatterns.infoField` stops at the first space because its character class excludes
     * one, which truncates `Info: UPI/DR/5123…/SWIGGY/UTIB/swiggy@ybl` mid-field and throws
     * away the payee. Parsers need the complete narration both as the human-readable
     * description and as the only place the counterparty is named in HDFC's `UPDATE:` form.
     */
    fun infoFieldFull(body: String): String? {
        val raw = INFO_FIELD.find(body)?.groupValues?.get(1)?.trim() ?: return null
        val padded = " ${raw.lowercase()} "
        val cut = INFO_TAIL_MARKERS.mapNotNull { marker ->
            padded.indexOf(marker).takeIf { it >= 0 }
        }.minOrNull()
        val trimmed = (if (cut != null) raw.substring(0, cut) else raw)
            .trim().trim('.', ',', ';', '-')
        return trimmed.takeIf { it.length >= 2 }
    }

    /** Rail and bookkeeping tokens that occupy a narration slot but name nobody. */
    private val NARRATION_RAILS = setOf(
        "upi", "cr", "dr", "p2m", "p2a", "p2p", "neft", "imps", "rtgs", "mmt", "ach", "nach",
        "ecs", "atw", "bil", "eba", "inf", "chg", "si", "ib", "mb", "pos", "ecom", "ft", "tpt",
        "cms", "clg", "byclg", "rev", "int", "sal",
    )

    /** Narrations that are a remark rather than a payee; treating them as merchants is worse than null. */
    private val NARRATION_REMARKS = setOf(
        "payment", "payments", "paid", "transfer", "fund transfer", "money transfer", "collect",
        "sent", "received", "pay", "other", "others", "na", "null", "nil", "self", "upi txn",
        "payment from ph", "payment to ph", "upi payment", "no remarks", "none",
    )

    private val IFSC_LIKE = Regex("""^[A-Za-z]{4}\d{6,7}$""")

    private val BANK_SHORT_CODES = setOf(
        "HDFC", "ICIC", "SBIN", "UTIB", "KKBK", "IDFB", "YESB", "PYTM", "AIRP", "PUNB", "BARB",
        "CNRB", "UBIN", "IOBA", "IDIB", "MAHB", "KARB", "FDRL", "INDB", "RATN", "AUBL", "BKID",
        "CBIN", "JAKA", "TMBL", "SIBL", "CSBK", "DCBL", "ESFB", "UJVN", "EQTB", "IBKL", "SCBL",
        "CITI", "HSBC", "DEUT", "JSFB", "FINO", "SVCB", "TJSB", "ABHY", "BDBL", "KVBL", "LAVB",
    )

    /**
     * The counterparty hidden inside a `UPI/…`-style narration.
     *
     * Indian banks pack the payee into a slash- or dash-delimited narration whose field
     * order is not stable across issuers (`UPI/DR/<rrn>/<payee>/<bank>/<vpa>` at HDFC,
     * `UPI/<payee>/<rrn>/<remark>` elsewhere), so position-based extraction breaks the
     * moment a bank reorders. Skipping what a name provably is *not* — a rail token, a
     * reference number, an IFSC, a boilerplate remark — and taking the first survivor is
     * stable across all of those layouts.
     *
     * A VPA is only used as a last resort: `swiggy@ybl` identifies the payee but resolves
     * far worse than `SWIGGY` downstream.
     */
    fun merchantFromUpiInfoField(info: String?): String? {
        val narration = info?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val segments = if (narration.contains('/')) {
            narration.split('/')
        } else {
            narration.split('-')
        }
        var vpaFallback: String? = null
        for (segment in segments) {
            val candidate = segment.trim().trim('.', ',', ';', ':', '*', '#')
            if (candidate.length < 2 || candidate.length > 45) continue
            val lower = candidate.lowercase()
            if (candidate.none(Char::isLetter)) continue
            if (lower in NARRATION_RAILS || lower in NARRATION_REMARKS) continue
            if (NARRATION_REMARKS.any { lower.startsWith(it) && lower.length <= it.length + 4 }) continue
            if (IFSC_LIKE.matches(candidate)) continue
            if (candidate.uppercase() in BANK_SHORT_CODES) continue
            if (candidate.contains('@')) {
                if (vpaFallback == null) vpaFallback = candidate
                continue
            }
            return stripTrailingNoise(candidate) ?: continue
        }
        return vpaFallback
    }

    // -------------------------------------------------------------- amounts

    private val PREFIXED_AMOUNT = Regex(
        """(?:rs\.?|inr|₹)\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Amounts written the way a bank writes money — currency word first.
     *
     * `SmsPatterns.amounts` also accepts a trailing currency word, which is correct in
     * general but misreads Axis's card alerts: `Card no. XX1234 INR 1,250.00` makes the
     * card's last four digits look like an amount that appears *earlier* than the real one,
     * so the transacted figure becomes 1234. Restricting to the prefixed form removes that
     * whole class of confusion for the templates that use it.
     */
    fun prefixedAmounts(body: String, currency: String = Money.INR): List<Money> =
        PREFIXED_AMOUNT.findAll(body).mapNotNull { Money.parse(it.groupValues[1], currency) }.toList()

    private val AVAILABLE_LIMIT = Regex(
        """(?:avl|avbl|available|avail)\.?\s*(?:credit\s*)?(?:lmt|limit)\s*[:\-]?\s*(?:is\s*)?(?:rs\.?|inr|₹)?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The card's available *limit*, which is spending headroom and emphatically not a balance.
     *
     * Axis, ICICI, Kotak and IDFC FIRST all print it in the slot where a savings-account
     * alert prints the balance. Storing it as `balanceAfter` would graft a credit line onto
     * the account's balance history and corrupt every net-worth figure derived from it, so
     * parsers surface it in `extractedFields` instead.
     */
    fun availableLimit(body: String, currency: String = Money.INR): Money? =
        AVAILABLE_LIMIT.find(body)?.groupValues?.get(1)?.let { Money.parse(it, currency) }

    private val REAL_BALANCE = Regex(
        """(?:avl|avbl|available|closing|clear|clr|new|updated|a/?c)\.?\s*(?:bal|balance)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** True when the message states a genuine account balance, as opposed to a credit limit. */
    fun statesRealBalance(body: String): Boolean = REAL_BALANCE.containsMatchIn(body)

    // ------------------------------------------------------------ references

    private val REFERENCE_PHRASES = listOf(
        // "Your UPI transaction reference number is 512345678901" — HDFC's long form, which
        // the shared pattern loses because it captures the word "reference" itself first.
        Regex("""\bref(?:erence)?\s*(?:no\.?|number|id)?\s*(?:is)?\s*[:\-#]?\s*(\d{9,18})\b""", RegexOption.IGNORE_CASE),
        // "UPI:512345678901", "UPI Ref 512345678901", "UPI/DR/512345678901/…"
        Regex("""\bupi\s*(?:ref(?:erence)?)?\s*(?:no\.?|id)?\s*[:\-#/]\s*(?:[A-Z]{2,3}/)?(\d{9,18})\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:utr|rrn)\s*(?:no\.?|is)?\s*[:\-#]?\s*([A-Z0-9]{9,22})\b""", RegexOption.IGNORE_CASE),
    )

    /**
     * The transaction reference, for the phrasings the shared pattern cannot reach.
     *
     * `SmsPatterns.referenceNumber` requires the digits to follow the label directly, so a
     * slash-delimited `UPI/DR/5123…` or a sentence-shaped "reference number is 5123…"
     * yields nothing. A reference is what de-duplication and dispute-tracking hang on, so
     * it is worth a second pass.
     */
    fun referenceNumber(body: String): String? {
        for (pattern in REFERENCE_PHRASES) {
            val hit = pattern.find(body)?.groupValues?.get(1)
            if (hit != null && hit.any(Char::isDigit)) return hit
        }
        return null
    }

    /** The first long numeric run inside a narration — HDFC and Axis both hide the RRN there. */
    fun referenceFromNarration(info: String?): String? =
        info?.let { Regex("""\b(\d{9,18})\b""").find(it)?.groupValues?.get(1) }

    // ------------------------------------------------------- shared templates

    private val COUNTERPARTY_CREDITED = Regex(
        """[;.]\s*([A-Za-z0-9][A-Za-z0-9 &.'@_\-]{1,45}?)\s+(?:is\s+|has\s+been\s+)?credited\b""",
        RegexOption.IGNORE_CASE,
    )

    private val ACCOUNT_OF_CREDITED = Regex(
        """\baccount\s+of\s+([A-Za-z0-9][A-Za-z0-9 &.'@_\-]{1,45}?)\s+(?:is\s+|has\s+been\s+)?credited\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The payee in the `… debited for Rs X on <date>; MERCHANT credited.` wording.
     *
     * ICICI wrote this template and IDFC FIRST copied it. It is the one common Indian shape
     * where the counterparty sits *after* the amount and is introduced by no preposition at
     * all, so the shared `to`/`at`/`from` patterns cannot see it — worse, they latch onto
     * the `for Rs X` clause and hand back the amount as the merchant.
     */
    fun counterpartyBeforeCredited(body: String): String? =
        stripTrailingNoise(
            ACCOUNT_OF_CREDITED.find(body)?.groupValues?.get(1)
                ?: COUNTERPARTY_CREDITED.find(body)?.groupValues?.get(1),
        )

    private val PAYEE_AFTER_DATE = Regex(
        """\bon\s+\d{1,2}[-/][A-Za-z]{3}[-/]\d{2,4}(?:\s*(?:at|,)?\s*\d{1,2}:\d{2}(?::\d{2})?)?\s+(?:at|on|to)\s+([A-Za-z0-9][A-Za-z0-9 &.'*_\-]{1,45}?)\s*[.;,]""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * The merchant in ICICI's card wording, which puts it after the date: `… on 05-Aug-25 on
     * AMAZON.` The shared `at <name>` pattern matches the *anti-fraud footer* instead
     * ("To dispute, call…"), which is how "dispute" ends up looking like a merchant.
     */
    fun payeeAfterDate(body: String): String? =
        stripTrailingNoise(PAYEE_AFTER_DATE.find(body)?.groupValues?.get(1))

    private val VPA_AFTER_PREPOSITION = Regex(
        """\b(?:by|from|to|vpa)\s+([a-zA-Z0-9._\-]{2,64}@[a-zA-Z]{2,20})\b""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * A VPA introduced by `by`, which the shared merchant patterns skip on purpose: `by`
     * usually names the rail ("by NEFT", "by ATM WDL"). SBI's UPI credits are the exception
     * — `credited by rajesh@okaxis` names the payer — and a VPA is unambiguous enough to
     * make the exception safe.
     */
    fun vpaCounterparty(body: String): String? =
        VPA_AFTER_PREPOSITION.find(body)?.groupValues?.get(1)

}

/**
 * Rewrites the audit fields so Parser Logs show what the parser *concluded*, not what
 * generic extraction guessed before the refinement corrected it. A stale `merchant` entry
 * sitting beside a corrected one is worse than no entry: it makes a fixed bug look live.
 */
internal fun ParsedTransaction.withParserFields(
    template: String,
    extra: Map<String, String> = emptyMap(),
): ParsedTransaction {
    val merchant = rawMerchant
    val reference = referenceNumber
    val account = maskedAccount
    val vpa = upiId
    val fields = extractedFields.toMutableMap()
    fields["template"] = template
    fields["direction"] = direction.name
    if (merchant != null) fields["merchant"] = merchant else fields.remove("merchant")
    if (reference != null) fields["reference"] = reference else fields.remove("reference")
    if (account != null) fields["account"] = account else fields.remove("account")
    if (vpa != null) fields["upi"] = vpa else fields.remove("upi")
    fields.putAll(extra)
    return copy(extractedFields = fields)
}
