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
 * Courier and e-commerce delivery updates: shipped, out-for-delivery and delivered
 * notifications from Amazon, Flipkart, Delhivery, BlueDart, Ekart, DTDC, India Post, and
 * "your order has been delivered" messages from Swiggy/Zomato.
 *
 * These are pure lifecycle notices, not transactions — [com.lifeledger.sms.parser.BaseBankParser]
 * would never claim them anyway (no amount, no direction) — but the timeline should still
 * show "parcel delivered" as an event, and a courier update reliably names a tracking id
 * worth surfacing.
 *
 * `AMAZON` is one of the sender codes `com.lifeledger.sms.parser.wallets.AmazonPayParser`
 * also claims. This parser runs at a lower priority number (6 vs 15), so it gets first
 * refusal on any Amazon-sent message; delivery-shaped ones are claimed here and never reach
 * the payment parser, financial ones simply do not match this parser's delivery vocabulary
 * and fall through.
 */
class DeliveryParser : SmsParser {

    override val info = ParserInfo(
        id = "delivery",
        displayName = "Delivery",
        version = 1,
        senderCodes = emptySet(),
        priority = 6,
        description = "Courier and e-commerce delivery lifecycle updates.",
    )

    override fun canHandle(sms: SmsRecord): Boolean = stageOf(sms.body) != null

    override fun parse(sms: SmsRecord, context: ParserContext): ParseResult {
        val body = sms.body
        if (Lexicon.looksPromotional(body)) return ParseResult.Ignored(info.id, "promotional content")

        val stage = stageOf(body) ?: return ParseResult.NotApplicable
        val courier = KeywordTable.firstMatch(body, COURIERS)
        val trackingId = TRACKING.find(body)?.groupValues?.get(1)

        // A bare "delivered"/"shipped" without a recognised courier and without a tracking
        // id is too weak a signal on its own — it could be almost any sentence ("food was
        // delivered hot"). Require at least one of the two before claiming the message.
        if (courier == null && trackingId == null) return ParseResult.NotApplicable

        var confidence = 0.55f
        if (courier != null) confidence += 0.2f
        if (trackingId != null) confidence += 0.15f

        return ParseResult.Success(
            ParsedTransaction(
                amount = null,
                type = TransactionType.DELIVERY,
                direction = Direction.NEUTRAL,
                paymentMethod = PaymentMethod.UNKNOWN,
                occurredAt = SmsPatterns.instantIn(body, sms.receivedAt, context.zone),
                rawMerchant = courier,
                confidence = Confidence.of(confidence),
                extractedFields = buildMap {
                    put("stage", stage)
                    courier?.let { put("courier", it) }
                    trackingId?.let { put("tracking_id", it) }
                },
            ),
            info.id,
        )
    }

    private fun stageOf(body: String): String? {
        val lower = body.lowercase()
        return STAGE_WORDS.firstOrNull { (_, words) -> words.any { lower.contains(it) } }?.first
    }

    private companion object {
        // Order matters: a message reporting several stages ("shipped, now out for
        // delivery") is describing its *latest* status, so the most advanced stage must be
        // checked first.
        val STAGE_WORDS = listOf(
            "DELIVERED" to listOf("delivered", "delivery completed"),
            "OUT_FOR_DELIVERY" to listOf("out for delivery", "out-for-delivery"),
            "SHIPPED" to listOf("shipped", "dispatched", "has been shipped", "picked up"),
        )

        val COURIERS = listOf(
            "amazon" to "Amazon",
            "flipkart" to "Flipkart",
            "delhivery" to "Delhivery",
            "blue dart" to "BlueDart",
            "bluedart" to "BlueDart",
            "ekart" to "Ekart",
            "dtdc" to "DTDC",
            "swiggy" to "Swiggy",
            "zomato" to "Zomato",
            "india post" to "India Post",
            "indiapost" to "India Post",
            "speed post" to "India Post",
        )

        val TRACKING = Regex(
            """(?:awb(?:\s*no\.?)?|tracking\s*(?:id|no\.?)?|consignment\s*(?:no\.?)?|order\s*id)\s*[:\-]?\s*([A-Z0-9]{6,20})""",
            RegexOption.IGNORE_CASE,
        )
    }
}
