package com.lifeledger.sms.parser.wallets

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class CreditCardParserTest {

    private val parser = CreditCardParser()

    @Test
    fun `spend with explicit credit card wording and available limit`() {
        val result = parser.parse(
            sms(
                "HDFCBK",
                "Rs.2,499.00 spent on your HDFC Bank Credit Card XX1234 at AMAZON on 05-Aug-26. Avl Lmt: Rs.47,501.00.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.PURCHASE)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.CARD_CREDIT)
        assertThat(success.transaction.amount).isNotNull()
        assertThat(success.transaction.balanceAfter).isNull()
        assertThat(success.transaction.extractedFields["available_limit"]).isEqualTo("47,501.00")
    }

    @Test
    fun `spend claimed via bare card plus available limit without the word credit`() {
        val result = parser.parse(
            sms(
                "ICICIB",
                "Rs.1,200.00 spent using your Card XX9988 at BigBasket. Available limit: Rs.30,000.00.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.CARD_CREDIT)
        assertThat(success.transaction.extractedFields["available_limit"]).isEqualTo("30,000.00")
    }

    @Test
    fun `statement due message is rewritten to BILL_DUE with no amount movement`() {
        val result = parser.parse(
            sms(
                "HDFCBK",
                "Your HDFC Bank Credit Card XX1234 Statement is generated. Total Amt Due: Rs.15,000.00, " +
                    "Min Amt Due: Rs.750.00, Due Date: 25-Aug-2026. Unpaid dues will be auto-debited from your " +
                    "account on due date.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.BILL_DUE)
        assertThat(success.transaction.amount).isNull()
        assertThat(success.transaction.direction).isEqualTo(Direction.NEUTRAL)
        assertThat(success.transaction.balanceAfter).isNull()
        assertThat(success.transaction.extractedFields["total_due"]).isEqualTo("15,000.00")
        assertThat(success.transaction.extractedFields["min_due"]).isEqualTo("750.00")
    }

    @Test
    fun `minimum amount due phrasing variant is also detected as BILL_DUE`() {
        val result = parser.parse(
            sms(
                "ICICIB",
                "ICICI Bank Credit Card: your bill is generated. Minimum Amount Due Rs.900.00. If not paid by " +
                    "the due date, late fee will apply.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.BILL_DUE)
        assertThat(success.transaction.amount).isNull()
    }

    @Test
    fun `statement message with both limit and due figures keeps both fields`() {
        val result = parser.parse(
            sms(
                "AXISBK",
                "Axis Bank Credit Card XX4321 statement generated. Total Amt Due Rs.8,000.00. Avl Lmt Rs.42,000.00. " +
                    "Unpaid amount will be auto-debited from your linked account.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.BILL_DUE)
        assertThat(success.transaction.extractedFields["available_limit"]).isEqualTo("42,000.00")
        assertThat(success.transaction.extractedFields["total_due"]).isEqualTo("8,000.00")
    }

    @Test
    fun `debit card message is not claimed`() {
        val record = sms(
            "SBIINB",
            "Rs.500.00 spent using your Debit Card XX1234 at BigBazaar. Avl Bal Rs.10,000.00.",
        )
        assertThat(parser.canHandle(record)).isFalse()
    }

    @Test
    fun `unrelated bank message without card mention is not claimed`() {
        val record = sms("HDFCBK", "Rs.500.00 debited from A/c XX1234 for UPI payment to Merchant XYZ.")
        assertThat(parser.canHandle(record)).isFalse()
    }

    @Test
    fun `promotional credit card offer is ignored`() {
        val result = parser.parse(
            sms(
                "HDFCBK",
                "Get flat cashback up to Rs.500 on your HDFC Bank Credit Card! Click here to apply now. T&C apply.",
            ),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `OTP mentioning credit card is ignored not parsed as a transaction`() {
        val result = parser.parse(
            sms(
                "HDFCBK",
                "Do not share OTP 334455 with anyone for your Credit Card transaction of Rs.2000.",
            ),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `spend without available limit still succeeds and leaves balanceAfter null`() {
        val result = parser.parse(
            sms("KOTAKBK", "Rs.3,200.00 spent on your Kotak Credit Card XX5566 at Croma on 04-Aug-26."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.CARD_CREDIT)
        assertThat(success.transaction.balanceAfter).isNull()
        assertThat(success.transaction.extractedFields).doesNotContainKey("available_limit")
    }

    @Test
    fun `signature match with credit card wording and limit raises confidence`() {
        val result = parser.parse(
            sms(
                "HDFCBK",
                "Rs.1,500.00 spent on your HDFC Bank Credit Card XX2233 at Amazon. Avl Lmt: Rs.20,000.00.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.confidence.value).isAtLeast(0.7f)
    }

    @Test
    fun `a card statement message with no in-body amount or direction word is currently declined`() {
        // BaseBankParser requires a non-neutral direction before it will even build a
        // draft (see CreditCardParser's class doc). A statement message that carries no
        // debit/credit vocabulary at all never reaches this parser's refine() step, so it
        // is correctly NotApplicable rather than a false BILL_DUE.
        val result = parser.parse(
            sms("HDFCBK", "Your HDFC Bank Credit Card statement is available online. Visit netbanking to view."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }
}
