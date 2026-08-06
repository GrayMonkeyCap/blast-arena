package com.lifeledger.sms.parser.wallets

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class PaytmParserTest {

    private val parser = PaytmParser()

    @Test
    fun `UPI payment through Paytm Payments Bank is claimed as a debit`() {
        val result = parser.parse(
            sms("PAYTMB", "Rs.220.00 paid to Reliance Fresh via Paytm Payments Bank UPI. UPI Ref No 112233445566."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.UPI)
        assertThat(success.transaction.bankCode).isEqualTo("PAYTM")
    }

    @Test
    fun `wallet top-up sets payment method to WALLET`() {
        val result = parser.parse(
            sms("PAYTM", "Rs.1000.00 added to your Paytm Wallet from Bank A/c XX5566. Order ID PWL9988776655."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
        assertThat(success.transaction.direction).isEqualTo(Direction.CREDIT)
    }

    @Test
    fun `wallet spend that also mentions UPI still resolves to WALLET`() {
        val result = parser.parse(
            sms(
                "PYTMPB",
                "Rs.150.00 paid to Swiggy using Paytm Wallet via UPI. UPI Ref No 445566778899.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
    }

    @Test
    fun `PPBL account credit is claimed`() {
        val result = parser.parse(
            sms("PYTMPB", "Rs.5000.00 credited to your Paytm Payments Bank A/c XX7788 by NEFT."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.CREDIT)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.NEFT)
    }

    @Test
    fun `bill payment through Paytm is claimed`() {
        val result = parser.parse(
            sms("PAYTM", "Rs.450.00 paid towards DTH Recharge via Paytm. UPI Ref No 998877665544."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
    }

    @Test
    fun `refund to Paytm wallet is claimed as a credit`() {
        val result = parser.parse(
            sms("PAYTMB", "Rs.99.00 refunded to your Paytm Wallet for a cancelled order."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.REFUND)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
    }

    @Test
    fun `promotional cashback campaign is ignored`() {
        val result = parser.parse(
            sms(
                "PAYTM",
                "Exclusive offer! Add money to Paytm Wallet and get flat 50% cashback. Hurry, limited period offer.",
            ),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `bare OTP message is ignored`() {
        val result = parser.parse(
            sms("PAYTM", "556677 is your OTP for Paytm login. Valid for 10 minutes. Do not share it."),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `informational KYC message with no amount is not applicable`() {
        val result = parser.parse(
            sms("PAYTM", "Your Paytm KYC verification is complete."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `messages from unrelated sender codes are not claimed`() {
        val record = sms("SBIINB", "Rs.300 debited from A/c XX1122 to Merchant DEF.")
        assertThat(parser.canHandle(record)).isFalse()
    }

    @Test
    fun `signature match on paytm and ppbl mention raises confidence`() {
        val result = parser.parse(
            sms(
                "PYTMPB",
                "Rs.500.00 paid to a merchant via Paytm Payments Bank PPBL UPI. UPI Ref No 776655443322.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.confidence.value).isAtLeast(0.7f)
    }

    @Test
    fun `VPA is extracted as upiId for a UPI flow`() {
        val result = parser.parse(
            sms("PAYTMB", "Rs.60.00 paid to shop@paytm via Paytm. UPI Ref No 334455667788."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.upiId).isEqualTo("shop@paytm")
    }
}
