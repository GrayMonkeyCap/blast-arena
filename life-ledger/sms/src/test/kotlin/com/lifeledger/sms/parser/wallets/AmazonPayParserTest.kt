package com.lifeledger.sms.parser.wallets

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class AmazonPayParserTest {

    private val parser = AmazonPayParser()

    @Test
    fun `UPI payment to merchant is claimed as a debit`() {
        val result = parser.parse(
            sms("AMZNPY", "Rs.599.00 paid to BigBasket using Amazon Pay UPI. UPI Ref No 112233445566."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.UPI)
        assertThat(success.transaction.bankCode).isEqualTo("AMAZONPAY")
    }

    @Test
    fun `Amazon Pay balance top-up sets payment method to WALLET`() {
        val result = parser.parse(
            sms("APAYIN", "Rs.1000.00 added to your Amazon Pay balance from Bank A/c XX3344."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
        assertThat(success.transaction.direction).isEqualTo(Direction.CREDIT)
    }

    @Test
    fun `Amazon Pay balance spend sets payment method to WALLET`() {
        val result = parser.parse(
            sms("AMAZON", "Rs.249.00 debited from your Amazon Pay balance for order at Amazon.in."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
    }

    @Test
    fun `refund to Amazon Pay balance is claimed as a credit`() {
        val result = parser.parse(
            sms("AMZNPY", "Rs.349.00 refunded to your Amazon Pay balance for a returned item."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.REFUND)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
    }

    @Test
    fun `bill payment via Amazon Pay UPI is claimed`() {
        val result = parser.parse(
            sms("APAYIN", "Rs.720.00 paid towards Broadband Bill via Amazon Pay to ACT Fibernet. UPI Ref No 223344556677."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
    }

    @Test
    fun `cashback credited via Amazon Pay is claimed`() {
        val result = parser.parse(
            sms("AMZNPY", "Cashback of Rs.30.00 credited to your Amazon Pay balance for a recent purchase."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.CASHBACK)
    }

    @Test
    fun `promotional recharge offer is ignored`() {
        val result = parser.parse(
            sms(
                "AMZNPY",
                "Get a lucky winner surprise! Recharge using Amazon Pay and you have won a prize. Click here.",
            ),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `bare OTP message is ignored`() {
        val result = parser.parse(
            sms("APAYIN", "778899 is your OTP for Amazon Pay verification. Do not share this OTP."),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `informational linking message with no amount is not applicable`() {
        val result = parser.parse(
            sms("AMZNPY", "Your Amazon Pay account is now linked to ICICI Bank A/c XX5566."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `messages from unrelated sender codes are not claimed`() {
        val record = sms("AXISBK", "Rs.400 debited from A/c XX7788 to Merchant GHI.")
        assertThat(parser.canHandle(record)).isFalse()
    }

    @Test
    fun `signature match on amazon pay and apay mention raises confidence`() {
        val result = parser.parse(
            sms("AMZNPY", "Rs.150.00 paid to a merchant using Amazon Pay (Apay) UPI. UPI Ref No 998877001122."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.confidence.value).isAtLeast(0.7f)
    }

    @Test
    fun `sender code AMAZON is recognised for Amazon Pay financial messages`() {
        val record = sms("AMAZON", "Rs.99.00 paid using Amazon Pay UPI for an order. UPI Ref No 112233445566.")
        assertThat(parser.canHandle(record)).isTrue()
    }
}
