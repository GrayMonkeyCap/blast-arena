package com.lifeledger.sms.parser.life

import com.lifeledger.core.model.Confidence
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.ParsedTransaction
import com.lifeledger.core.model.ParserInfo
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.SmsRecord
import com.lifeledger.core.model.TransactionType
import com.lifeledger.sms.api.ParserContext
import com.lifeledger.sms.api.SmsParser
import com.lifeledger.sms.lex.Lexicon
import com.lifeledger.sms.lex.SmsPatterns

/**
 * Travel and event bookings: flights, trains (IRCTC), buses, hotels, movies (BookMyShow)
 * and cabs.
 *
 * A confirmation message ("PNR CNF123, seat 14A") and the *payment* for that booking often
 * arrive as two separate SMS from two different senders (the OTA/airline, then the bank).
 * This parser only ever produces the non-financial confirmation event — `amount = null` —
 * because the payment side is already fully covered by the bank/UPI parsers; duplicating it
 * here would double-count the spend on the timeline.
 */
class BookingParser : SmsParser {

    override val info = ParserInfo(
        id = "booking",
        displayName = "Booking",
        version = 1,
        senderCodes = emptySet(),
        priority = 6,
        description = "Flight, train, bus, hotel, movie and cab booking confirmations.",
    )

    override fun canHandle(sms: SmsRecord): Boolean = KeywordTable.anyMatch(sms.body, BOOKING_WORDS)

    override fun parse(sms: SmsRecord, context: ParserContext): ParseResult {
        val body = sms.body
        if (Lexicon.looksPromotional(body)) return ParseResult.Ignored(info.id, "promotional content")
        if (!KeywordTable.anyMatch(body, BOOKING_WORDS)) return ParseResult.NotApplicable

        val pnr = PNR.find(body)?.groupValues?.get(1)
        val provider = KeywordTable.firstMatch(body, PROVIDERS)
        val route = ROUTE.find(body)?.let { m -> "${m.groupValues[1].trim()} to ${m.groupValues[2].trim()}" }

        // A generic word like "seat" or "check-in" alone is too weak: require a PNR or a
        // recognised provider before turning the message into a timeline event.
        if (pnr == null && provider == null) return ParseResult.NotApplicable

        var confidence = 0.5f
        if (pnr != null) confidence += 0.2f
        if (provider != null) confidence += 0.15f
        if (route != null) confidence += 0.05f

        return ParseResult.Success(
            ParsedTransaction(
                amount = null,
                type = TransactionType.BOOKING,
                direction = Direction.NEUTRAL,
                paymentMethod = PaymentMethod.UNKNOWN,
                occurredAt = SmsPatterns.instantIn(body, sms.receivedAt, context.zone),
                rawMerchant = provider,
                confidence = Confidence.of(confidence),
                extractedFields = buildMap {
                    pnr?.let { put("pnr", it) }
                    provider?.let { put("provider", it) }
                    route?.let { put("route", it) }
                    SmsPatterns.dateIn(body)?.let { put("travel_date", it.toString()) }
                },
            ),
            info.id,
        )
    }

    private companion object {
        val BOOKING_WORDS = listOf(
            "pnr", "flight", "boarding pass", "boarding", "check-in", "itinerary",
            "e-ticket", "eticket", "irctc", "train no", "coach", "berth", "seat no",
            "chart prepared", "bus ticket", "boarding point", "redbus", "abhibus",
            "hotel booking", "room booked", "reservation confirmed", "check-out", "oyo",
            "bookmyshow", "movie ticket", "show time", "cab confirmed", "trip id",
            "ride confirmed", "your ola", "your uber",
        )

        val PROVIDERS = listOf(
            "indigo" to "IndiGo",
            "spicejet" to "SpiceJet",
            "air india" to "Air India",
            "vistara" to "Vistara",
            "go first" to "Go First",
            "akasa" to "Akasa Air",
            "irctc" to "IRCTC",
            "redbus" to "RedBus",
            "abhibus" to "AbhiBus",
            "makemytrip" to "MakeMyTrip",
            "goibibo" to "Goibibo",
            "yatra" to "Yatra",
            "oyo" to "OYO",
            "bookmyshow" to "BookMyShow",
            "ola" to "Ola",
            "uber" to "Uber",
        )

        val PNR = Regex("""\bpnr\s*(?:no\.?|number)?\s*[:\-]?\s*([A-Z0-9]{5,10})""", RegexOption.IGNORE_CASE)

        val ROUTE = Regex(
            """\bfrom\s+([A-Za-z][A-Za-z ]{1,25}?)\s+to\s+([A-Za-z][A-Za-z ]{1,25}?)(?:[.,;\n]|\s+on\b|\s+dep|$)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
