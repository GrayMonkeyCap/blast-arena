package com.lifeledger.sms.parser.wallets

import com.google.common.truth.Truth.assertThat
import com.lifeledger.core.model.Direction
import com.lifeledger.core.model.ParseResult
import com.lifeledger.core.model.PaymentMethod
import com.lifeledger.core.model.TransactionType
import org.junit.Test

class GooglePayParserTest {

    private val parser = GooglePayParser()

    @Test
    fun `UPI payment to merchant is claimed as a debit`() {
        val result = parser.parse(
            sms("GOOGLE", "Rs.349.00 paid to Domino's Pizza using Google Pay. UPI Ref No 112233445566."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.UPI)
        assertThat(success.transaction.bankCode).isEqualTo("GPAY")
    }

    @Test
    fun `UPI receipt from a person is claimed as a credit`() {
        val result = parser.parse(
            sms("GPAY", "Rs.2000.00 received from Priya Nair via Google Pay. UPI Ref No 998877001122."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.CREDIT)
        assertThat(success.transaction.type).isEqualTo(TransactionType.TRANSFER_IN)
    }

    @Test
    fun `bill payment via Google Pay UPI is claimed`() {
        val result = parser.parse(
            sms("GOOGPY", "Rs.650.00 paid towards Mobile Bill via Google Pay to Airtel. UPI Ref No 223344556677."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.direction).isEqualTo(Direction.DEBIT)
        assertThat(success.transaction.paymentMethod).isEqualTo(PaymentMethod.UPI)
    }

    @Test
    fun `cashback credited through Google Pay is claimed`() {
        val result = parser.parse(
            sms("GOOGLE", "Cashback of Rs.20.00 credited to your account for a recent Google Pay transaction."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.CASHBACK)
        assertThat(success.transaction.direction).isEqualTo(Direction.CREDIT)
    }

    @Test
    fun `reversal is claimed as a credit`() {
        val result = parser.parse(
            sms("GPAY", "Rs.199.00 reversed to your account. Google Pay UPI Ref No 334455667788."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.type).isEqualTo(TransactionType.REVERSAL)
    }

    @Test
    fun `VPA is extracted as upiId`() {
        val result = parser.parse(
            sms("GOOGLE", "Rs.150.00 paid to shop@okaxis using Google Pay. UPI Ref No 445566778899."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.upiId).isEqualTo("shop@okaxis")
    }

    @Test
    fun `promotional referral offer is ignored`() {
        val result = parser.parse(
            sms(
                "GOOGLE",
                "Refer and earn Rs.100 for every friend who joins Google Pay! Download the app now. T&C apply.",
            ),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `bare OTP message is ignored`() {
        val result = parser.parse(
            sms("GPAY", "334455 is your OTP for Google Pay verification. Do not share this code."),
            testContext,
        )
        assertThat(result).isInstanceOf(ParseResult.Ignored::class.java)
    }

    @Test
    fun `informational message with no amount is not applicable`() {
        val result = parser.parse(
            sms("GOOGLE", "Your Google Pay account is now verified with HDFC Bank."),
            testContext,
        )
        assertThat(result).isEqualTo(ParseResult.NotApplicable)
    }

    @Test
    fun `messages from unrelated sender codes are not claimed`() {
        val record = sms("ICICIB", "Rs.750 debited from A/c XX9988 to Merchant ABC.")
        assertThat(parser.canHandle(record)).isFalse()
    }

    @Test
    fun `signature match with explicit brand and upi mention raises confidence`() {
        val result = parser.parse(
            sms("GOOGLE", "Rs.90.00 paid to a merchant using Google Pay UPI. UPI Ref No 665544332211."),
            testContext,
        )
        val success = result as ParseResult.Success
        assertThat(success.transaction.confidence.isActionable).isTrue()
    }

    @Test
    fun `sender code GOOGPY is recognised`() {
        val record = sms("GOOGPY", "Rs.10.00 paid to a merchant using Google Pay. UPI Ref No 111122223333.")
        assertThat(parser.canHandle(record)).isTrue()
    }
}
