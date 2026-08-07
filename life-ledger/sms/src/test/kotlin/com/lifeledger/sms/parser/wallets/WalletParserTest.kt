package com.lifeledger.sms.parser.wallets

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.Money
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import org.junit.Test

class WalletParserTest {

    private val parser = WalletParser()

    @Test
    fun `Mobikwik wallet top-up is claimed as a WALLET credit`() {
        val result = parser.parse(
            sms(
                "AD-MOBIKWIK",
                "Rs.500.00 added to your Mobikwik Wallet from HDFC Bank A/c XX1234. Ref No 998877665544.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
        assertThat(success.transaction.direction).isEqualTo(Direction.CREDIT)
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("Mobikwik")
    }

    @Test
    fun `Freecharge wallet spend is claimed as a WALLET debit`() {
        val result = parser.parse(
            sms("VM-FRECHG", "Rs.199.00 paid to Bigbasket using Freecharge Wallet. Txn ID FC12345678."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("Freecharge")
    }

    @Test
    fun `Amazon Pay balance top-up is claimed as a WALLET credit`() {
        val result = parser.parse(
            sms("AMAZONPAY", "Rs.1000.00 added to your Amazon Pay balance from Bank A/c XX5678."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("Amazon Pay")
    }

    @Test
    fun `PhonePe Wallet reached through a generic sender is still claimed`() {
        val result = parser.parse(
            sms("AD-PHONEPE", "Rs.300.00 debited from your PhonePe Wallet for DTH Recharge."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
        assertThat(success.transaction.extractedFields["provider"]).isEqualTo("PhonePe Wallet")
    }

    @Test
    fun `wallet mention wins over UPI mention in the same message`() {
        val result = parser.parse(
            sms(
                "AD-PAYTM",
                "Rs.150.00 paid to Swiggy using Paytm Wallet via UPI. UPI Ref No 445566778899.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
    }

    @Test
    fun `plain bank NEFT credit with no wallet mention is not claimed`() {
        val record = sms("HDFCBK", "Rs.5000.00 credited to your Savings A/c XX1234 by NEFT.")
        assertThat(parser.canHandle(record)).isFalse()
    }

    @Test
    fun `promotional wallet top-up offer is ignored`() {
        val result = parser.parse(
            sms("AD-MOBIKWIK", "Add money to your Wallet and get 10% cashback! Limited period offer, click here."),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `OTP mentioning a wallet is ignored`() {
        val result = parser.parse(
            sms("VM-MBKWIK", "334455 is your OTP to link Mobikwik Wallet. Do not share this OTP."),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `informational KYC message with no amount is not applicable`() {
        val result = parser.parse(
            sms("AD-MOBIKWIK", "Your Mobikwik Wallet KYC is now complete."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `a recognised provider raises confidence over a bare generic wallet mention`() {
        val known = parser.parse(
            sms("AD-MOBIKWIK", "Rs.100.00 credited to your Mobikwik Wallet from Bank A/c XX1234."),
            testContext,
        ) as ParseResult.Success
        val generic = parser.parse(
            sms("AD-GENERIC", "Rs.100.00 credited to your Wallet from Bank A/c XX1234."),
            testContext,
        ) as ParseResult.Success

        assertThat(known.transaction.confidence.value).isGreaterThan(generic.transaction.confidence.value)
        assertThat(generic.transaction.extractedFields).doesNotContainKey("provider")
    }

    @Test
    fun `wallet balance figure is still captured generically`() {
        val result = parser.parse(
            sms("AD-MOBIKWIK", "Rs.50.00 debited from your Wallet for Recharge. Wallet Balance is Rs.450.00."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.balanceAfter).isEqualTo(Money.ofMajor(450))
    }
}
