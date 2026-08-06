package com.lifeledger.sms.parser.wallets

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class PhonePeParserTest {

    private val parser = PhonePeParser()

    @Test
    fun `UPI payment to merchant is claimed as a debit`() {
        val result = parser.parse(
            sms("PHONPE", "Rs.499.00 paid to Swiggy via PhonePe UPI. UPI Ref No 412345678901. -PhonePe"),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.UPI)
        assertThat(success.transaction.bankCode).isEqualTo("PHONEPE")
    }

    @Test
    fun `UPI receipt from a person is claimed as a credit`() {
        val result = parser.parse(
            sms("PHNPE", "Rs.1500.00 received from Rahul Sharma via PhonePe. UPI Ref No 998877665544."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.CREDIT)
        assertThat(success.transaction.type).isEqualTo(TransactionType.TRANSFER_IN)
    }

    @Test
    fun `wallet top-up sets payment method to WALLET not UPI`() {
        val result = parser.parse(
            sms(
                "PHONEPE",
                "Rs.500.00 added to your PhonePe Wallet from Bank A/c XX1234. Txn ID T2024080512345678.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
        assertThat(success.transaction.direction).isEqualTo(Direction.CREDIT)
    }

    @Test
    fun `wallet spend sets payment method to WALLET`() {
        val result = parser.parse(
            sms(
                "PHONEPE",
                "Rs.120.00 debited from your PhonePe Wallet for order at Zomato on 05-Aug-26. Txn ID T2024080598765432.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.WALLET)
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
    }

    @Test
    fun `refund is classified as REFUND with credit direction`() {
        val result = parser.parse(
            sms(
                "PHONPE",
                "Rs.250.00 refunded to your PhonePe account for cancelled order. UPI Ref No 112233445566.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.REFUND)
        assertThat(success.transaction.direction).isEqualTo(Direction.CREDIT)
    }

    @Test
    fun `bill payment through PhonePe UPI is still claimed`() {
        val result = parser.parse(
            sms(
                "PHONPE",
                "Rs.899.00 paid towards Electricity Bill via PhonePe UPI to BESCOM. UPI Ref No 334455667788.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.UPI)
    }

    @Test
    fun `promotional cashback offer is ignored not parsed as a transaction`() {
        val result = parser.parse(
            sms(
                "PHONPE",
                "Get 20% cashback up to Rs.100 on your next PhonePe recharge! Click here to know more. T&C apply.",
            ),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `bare OTP message from PhonePe sender is ignored`() {
        val result = parser.parse(
            sms("PHONPE", "445566 is your OTP for PhonePe login. Do not share with anyone."),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `informational message with no amount is not applicable`() {
        val result = parser.parse(
            sms("PHONPE", "Your PhonePe account is now linked to HDFC Bank A/c XX1234."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `messages from unrelated sender codes are not claimed`() {
        val record = sms("HDFCBK", "Rs.500 debited from A/c XX1234 to Merchant XYZ.")
        assertThat(parser.canHandle(record)).isFalse()
    }

    @Test
    fun `signature match with txn id and explicit brand mention raises confidence`() {
        val result = parser.parse(
            sms(
                "PHONEPE",
                "Rs.320.00 paid to CafeCoffeeDay via PhonePe UPI. UPI Ref No 667788990011. Txn ID T2024080511112222.",
            ),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.confidence.isActionable).isTrue()
        assertThat(success.transaction.confidence.value).isAtLeast(0.7f)
    }

    @Test
    fun `VPA is extracted as upiId for a UPI flow`() {
        val result = parser.parse(
            sms("PHONPE", "Rs.75.00 paid to merchant@ybl via PhonePe. UPI Ref No 556677889900."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.upiId).isEqualTo("merchant@ybl")
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.UPI)
    }
}
